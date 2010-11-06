/* 
 * Copyright 2009,2010 by the authors indicated in the @author tags. 
 * All rights reserved. 
 * 
 * See the LICENSE file for details.
 * 
 * Created by Guenter Bartsch on Mar 22, 2009
 */
package org.zamia.instgraph;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.zamia.BuildPath;
import org.zamia.DUManager;
import org.zamia.ERManager;
import org.zamia.ExceptionLogger;
import org.zamia.IZamiaMonitor;
import org.zamia.SourceLocation;
import org.zamia.Toplevel;
import org.zamia.ToplevelPath;
import org.zamia.ZamiaException;
import org.zamia.ZamiaException.ExCat;
import org.zamia.ZamiaLogger;
import org.zamia.ZamiaProfiler;
import org.zamia.ZamiaProject;
import org.zamia.instgraph.IGObject.IGObjectCat;
import org.zamia.util.HashSetArray;
import org.zamia.util.Pair;
import org.zamia.util.PathName;
import org.zamia.util.ZStack;
import org.zamia.vhdl.ast.Architecture;
import org.zamia.vhdl.ast.DUUID;
import org.zamia.vhdl.ast.DUUID.LUType;
import org.zamia.vhdl.ast.DesignUnit;
import org.zamia.vhdl.ast.VHDLPackage;
import org.zamia.zdb.ZDB;
import org.zamia.zdb.ZDBListIndex;
import org.zamia.zdb.ZDBMapIndex;

/**
 * Hibernate-based IG persistence manager
 * 
 * @author Guenter Bartsch
 * 
 */

public final class IGManager {

	protected final static ZamiaLogger logger = ZamiaLogger.getInstance();

	protected final static ExceptionLogger el = ExceptionLogger.getInstance();

	private static final String TCL_BUILD_ELABORATE_CMD = "zamiaBuildElaborate";

	private static final int NUM_THREADS = 1; // set to 1 to disable multithreading code

	private static final boolean ENABLE_MULTITHREADING = NUM_THREADS > 1;

	private static final String MODULE_IDX = "IGM_ModuleIdx"; // signature -> IGModule

	private static final String INSTANTIATORS_IDX = "IGM_InstantiatorsIdx";

	private static final String PACKAGE_IDX = "IGM_PackageIdx";

	private static final String SIGNATURES_IDX = "IGM_SignaturesIdx"; // uid -> HSA{signature, signature, ...}

	private final ZamiaProject fZPrj;

	private final ZDB fZDB;

	private final DUManager fDUM;

	private final ERManager fERM;

	// single thread only:
	private ZStack<BuildNodeJob> fTodoStack;

	// multi-threading:

	private Lock fLock;

	private Condition fModuleCreatedCond;

	private HashSet<String> fTodo;

	private HashSet<String> fModulesBeingCreated;

	private ExecutorService fExecutorService;

	private IZamiaMonitor fMonitor;

	private int fNumDone; // for progress reporting

	private static final boolean ENABLE_NEW_INDICES = false;

	private static final String STRUCT_INST_IDX = "IGM_StructInstIdx"; // struct dbid -> id -> InstMapInfo

	private ZDBMapIndex<String, IGInstMapInfo> fStructInstIdx;

	private static final String STRUCT_SIGNAL_IDX = "IGM_StructSignalIdx"; // struct dbid -> id -> SignalDBID

	private ZDBMapIndex<String, Long> fStructSignalIdx;

	private static final String SIGNAL_CONN_IDX = "IGM_SignalConnIdx"; // signal dbid -> dbid, dbid, ...

	private ZDBListIndex<Long> fSignalConnIdx;

	public IGManager(ZamiaProject aZPrj) {
		fZPrj = aZPrj;
		fZDB = fZPrj.getZDB();
		fDUM = fZPrj.getDUM();
		fERM = fZPrj.getERM();

		fStructInstIdx = new ZDBMapIndex<String, IGInstMapInfo>(STRUCT_INST_IDX, fZDB);
		fStructSignalIdx = new ZDBMapIndex<String, Long>(STRUCT_SIGNAL_IDX, fZDB);
		fSignalConnIdx = new ZDBListIndex<Long>(SIGNAL_CONN_IDX, fZDB);
	}

	private synchronized void updateStats(ToplevelPath aPath, String aSignature) {
		fNumDone++;
		logger.info("IGManager: %d modules done (%d todo ATM): building %s", fNumDone, getNumTodo(), aPath);
	}

	private class BuildNodeJob implements Runnable {

		public final ToplevelPath fPath;

		public final DUUID fDUUID;

		public final String fSignature;

		public final SourceLocation fLocation;

		public final ArrayList<Pair<String, IGStaticValue>> fActualGenerics;

		public final DUUID fParentDUUID;

		private IGManager fIGM;

		public BuildNodeJob(IGManager aIGM, ToplevelPath aPath, DUUID aParentDUUID, DUUID aDUUID, String aSignature, ArrayList<Pair<String, IGStaticValue>> aActualGenerics,
				SourceLocation aLocation) {
			fIGM = aIGM;
			fPath = aPath;
			fParentDUUID = aParentDUUID;
			fDUUID = aDUUID;
			fSignature = aSignature;
			fActualGenerics = aActualGenerics;
			fLocation = aLocation;
		}

		private void index(IGStructure aStruct, long aDBID) {

			/*
			 * index signals
			 */

			IGContainer container = aStruct.getContainer();

			if (container != null) {

				int n = container.getNumLocalItems();
				for (int i = 0; i < n; i++) {

					IGContainerItem item = container.getLocalItem(i);

					if (!(item instanceof IGObject)) {
						continue;
					}

					IGObject obj = (IGObject) item;

					if (obj.getCat() != IGObjectCat.SIGNAL) {
						continue;
					}

					//logger.info("IGManager: Indexing: DBID=%5d id=%s", aDBID, obj.getId());

					fStructSignalIdx.put(aDBID, obj.getId(), obj.getDBID());
				}
			}

			/*
			 * index statements
			 */

			int n = aStruct.getNumStatements();

			for (int i = 0; i < n; i++) {

				IGConcurrentStatement stmt = aStruct.getStatement(i);

				String label = stmt.getLabel();

				if (stmt instanceof IGInstantiation) {

					IGInstantiation inst = (IGInstantiation) stmt;

					long instDBID = inst.getDBID();

					String signature = inst.getSignature();

					IGModule childModule = findModule(signature);

					if (childModule != null) {

						IGInstMapInfo info = new IGInstMapInfo(childModule.getDBID(), label);

						int m = inst.getNumMappings();
						for (int j = 0; j < m; j++) {

							IGMapping mapping = inst.getMapping(j);

							IGMapInfo mapInfo = new IGMapInfo(instDBID, mapping);

							int l = mapInfo.getNumActualItems();
							for (int k = 0; k < l; k++) {
								IGItemAccess ai = mapInfo.getActualItem(k);

								IGItem item = ai.getItem();

								fSignalConnIdx.add(item.getDBID(), instDBID);
							}

							l = mapInfo.getNumFormalItems();
							for (int k = 0; k < l; k++) {
								IGItemAccess ai = mapInfo.getFormalItem(k);

								IGItem item = ai.getItem();

								info.addMapInfo(item.getDBID(), mapInfo);
							}
						}

						fStructInstIdx.put(aDBID, label, info);
					}

				} else if (stmt instanceof IGStructure) {

					IGStructure struct = (IGStructure) stmt;

					long childDBID = struct.getDBID();

					if (label != null && label.length() > 0) {
						IGInstMapInfo info = new IGInstMapInfo(childDBID, label);

						fStructInstIdx.put(aDBID, label, info);
					}

					index(struct, childDBID);

				} else if (stmt instanceof IGProcess) {
					IGProcess proc = (IGProcess) stmt;

					IGSequenceOfStatements sos = proc.getSequenceOfStatements();

					HashSetArray<IGItemAccess> accessedItems = new HashSetArray<IGItemAccess>();
					sos.computeAccessedItems(null, null, 0, accessedItems);

					int m = accessedItems.size();
					for (int j = 0; j < m; j++) {
						IGItemAccess ai = accessedItems.get(j);

						IGItem item = ai.getItem();

						fSignalConnIdx.add(item.getDBID(), fZDB.store(ai));
					}
				}
			}

		}

		@Override
		public void run() {
			IGModule module = null;

			try {
				try {

					module = getOrCreateIGModule(fPath, fParentDUUID, fDUUID, fSignature, fActualGenerics, false, fLocation);

					updateStats(fPath, fSignature);

					if (module.isStatementsElaborated()) {
						logger.error("IGManager: Internal error: module %s on todo list was already done!", fSignature);
					} else {

						DesignUnit du = fDUM.getDU(fDUUID);
						if (du instanceof Architecture) {

							Architecture arch = (Architecture) du;

							arch.computeStatementsIG(fIGM, module);

						} else {
							fERM.addError(new ZamiaException(ExCat.INTERMEDIATE, true, "IGManager: failed to find " + fDUUID, fLocation));
						}
					}
				} catch (ZamiaException e) {
					el.logException(e);
					fERM.addError(new ZamiaException(ExCat.INTERMEDIATE, true, e.getMessage(), e.getLocation()));
				}
			} catch (Throwable t) {
				el.logException(t);
			}

			if (ENABLE_MULTITHREADING) {
				fLock.lock();
			}
			if (module != null) {
				module.setStatementsElaborated(true);
				module.storeOrUpdate();

				if (ENABLE_NEW_INDICES) {
					logger.info("IGManager: Indexing %s", fSignature);
					index(module.getStructure(), module.getDBID());
				}
			}

			fTodo.remove(fSignature);

			if (ENABLE_MULTITHREADING) {
				fLock.unlock();
			}
		}
	}

	private int getNumTodo() {
		if (ENABLE_MULTITHREADING) {
			fLock.lock();
		}
		int n = fTodo.size();
		if (ENABLE_MULTITHREADING) {
			fLock.unlock();
		}
		return n;
	}

	private boolean isCanceled() {
		if (fMonitor == null) {
			return false;
		}
		return fMonitor.isCanceled();
	}

	private void initIGBuild() {
		fTodo = new HashSet<String>();

		if (ENABLE_MULTITHREADING) {

			fExecutorService = Executors.newFixedThreadPool(NUM_THREADS);

			fModulesBeingCreated = new HashSet<String>();
			fLock = new ReentrantLock();
			fModuleCreatedCond = fLock.newCondition();
		} else {
			fTodoStack = new ZStack<BuildNodeJob>();
		}
	}

	private void runIGBuild() {
		if (ENABLE_MULTITHREADING) {
			while (!isCanceled() && getNumTodo() > 0) {
				logger.info("IGManager: waiting, %d jobs todo...", getNumTodo());
				try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
					el.logException(e);
				}
			}

			try {
				if (isCanceled()) {
					fExecutorService.shutdownNow();
				} else {
					fExecutorService.shutdown();
				}
				fExecutorService.awaitTermination(7, TimeUnit.DAYS);
			} catch (InterruptedException e) {
				el.logException(e);
			}

		} else {
			while (!fTodoStack.isEmpty()) {

				if (isCanceled()) {
					logger.info("Canceled.");
					break;
				}

				BuildNodeJob job = fTodoStack.pop();
				job.run();
			}
		}
	}

	public IGModule buildIG(Toplevel aTL, IZamiaMonitor aMonitor, int aTotalUnits) {

		IGModule module = null;

		fMonitor = aMonitor;

		DUUID duuid = fDUM.getArchDUUID(aTL);

		if (duuid == null) {
			logger.error("IGManager: Failed to find toplevel %s.", aTL);
			fERM.addError(new ZamiaException(ExCat.INTERMEDIATE, true, "IGManager: failed to find toplevel " + aTL, aTL.getLocation()));
			return null;
		}

		//fTotalUnits = aTotalUnits;
		fNumDone = 0;

		String signature = IGInstantiation.computeSignature(duuid, null);

		initIGBuild();

		module = getOrCreateIGModule(new ToplevelPath(aTL, new PathName("")), null, duuid, signature, null, true, null);

		runIGBuild();

		//		ZamiaTclInterpreter zti = fZPrj.getZTI();
		//
		//		if (zti.hasCommand(TCL_BUILD_ELABORATE_CMD)) {
		//
		//			try {
		//				String cmd = TCL_BUILD_ELABORATE_CMD + " " + duuid.getLibId() + " " + duuid.getId() + " " + duuid.getArchId();
		//				logger.info("IGManager: tcl eval '%s'", cmd);
		//				zti.eval(cmd);
		//			} catch (TclException e) {
		//				el.logException(e);
		//			}
		//		}

		// too volatile to make sense
		//		if (fLastWorked < fTotalUnits) {
		//			worked(fTotalUnits - fLastWorked);
		//		}

		return module;
	}

	@SuppressWarnings("unchecked")
	public IGModule getOrCreateIGModule(ToplevelPath aPath, DUUID aParentDUUID, DUUID aDUUID, String aSignature, ArrayList<Pair<String, IGStaticValue>> aActualGenerics,
			boolean aElaborateStatements, SourceLocation aLocation) {

		if (ENABLE_MULTITHREADING) {
			fLock.lock();

			while (!isCanceled() && fModulesBeingCreated.contains(aSignature)) {

				try {
					fModuleCreatedCond.await();
				} catch (InterruptedException e) {
					el.logException(e);
				}

			}
		}

		IGModule module = null;

		long mid = fZDB.getIdx(MODULE_IDX, aSignature);

		if (mid != 0) {

			module = (IGModule) fZDB.load(mid);

		} else {

			try {
				DesignUnit du = fDUM.getDU(aDUUID);
				if (du instanceof Architecture) {

					Architecture arch = (Architecture) du;

					if (ENABLE_MULTITHREADING) {
						fModulesBeingCreated.add(aSignature);
						fLock.unlock();
					}

					module = new IGModule(aPath, aDUUID, arch.getLocation(), fZDB);

					int n = aActualGenerics != null ? aActualGenerics.size() : 0;
					for (int i = 0; i < n; i++) {
						module.addActualGeneric(aActualGenerics.get(i).getSecond());
					}

					arch.computeEntityIG(this, module);

					if (ENABLE_MULTITHREADING) {
						fLock.lock();
					}
					mid = module.storeOrUpdate();
					fZDB.putIdx(IGManager.MODULE_IDX, aSignature, mid);

					if (ENABLE_MULTITHREADING) {
						fModulesBeingCreated.remove(aSignature);
						fModuleCreatedCond.signal();
					}
				} else {
					fERM.addError(new ZamiaException(ExCat.INTERMEDIATE, true, "IGManager: failed to find " + aDUUID, aLocation));
				}
			} catch (ZamiaException e) {
				el.logException(e);
				fERM.addError(new ZamiaException(ExCat.INTERMEDIATE, true, e.getMessage(), e.getLocation()));
			}
		}

		if (module != null) {

			if (!module.isStatementsElaborated() && aElaborateStatements) {

				String uid = aDUUID.getUID();

				long dbid = fZDB.getIdx(SIGNATURES_IDX, uid);
				HashSetArray<String> signatures = new HashSetArray<String>();
				if (dbid == 0) {
					signatures = new HashSetArray<String>();
					signatures.add(aSignature);
					dbid = fZDB.store(signatures);
					fZDB.putIdx(IGManager.SIGNATURES_IDX, uid, dbid);
				} else {
					signatures = (HashSetArray<String>) fZDB.load(dbid);
					if (signatures.add(aSignature)) {
						fZDB.update(dbid, signatures);
					}
				}

				if (!fTodo.contains(aSignature)) {
					fTodo.add(aSignature);

					BuildNodeJob job = new BuildNodeJob(this, aPath, aParentDUUID, aDUUID, aSignature, aActualGenerics, aLocation);

					if (ENABLE_MULTITHREADING) {
						fExecutorService.execute(job);
					} else {
						fTodoStack.push(job);
					}
				}
			}
		}

		if (aParentDUUID != null) {
			// register instantiator

			String uid = aDUUID.getUID();

			mid = fZDB.getIdx(INSTANTIATORS_IDX, uid);
			if (mid != 0) {
				HashSetArray<DUUID> instantiators = (HashSetArray<DUUID>) fZDB.load(mid);

				if (!instantiators.contains(aParentDUUID)) {
					instantiators.add(aParentDUUID);
					fZDB.update(mid, instantiators);
				}
			} else {
				HashSetArray<DUUID> instantiators = new HashSetArray<DUUID>();

				instantiators.add(aParentDUUID);
				mid = fZDB.store(instantiators);
				fZDB.putIdx(INSTANTIATORS_IDX, uid, mid);
			}
		}

		if (ENABLE_MULTITHREADING) {
			fLock.unlock();
		}

		return module;
	}

	public IGModule findModule(String aSignature) {

		long id = fZDB.getIdx(MODULE_IDX, aSignature);
		if (id == 0) {
			return null;
		}

		IGModule module = (IGModule) fZDB.load(id);

		return module;
	}

	public ZamiaProject getProject() {
		return fZPrj;
	}

	public IGModule findModule(Toplevel aTL) {

		DUUID duuid = fDUM.getArchDUUID(aTL);

		if (duuid == null)
			return null;

		String signature = IGInstantiation.computeSignature(duuid, null);

		return findModule(signature);
	}

	public IGItem findItem(Toplevel aTL, PathName aPath) {

		IGItem item = null;

		IGModule module = findModule(aTL);

		if (module != null && aPath.getNumSegments() > 0) {

			item = module.getStructure();

			int n = aPath.getNumSegments();
			for (int i = 0; i < n; i++) {

				if (!(item instanceof IGConcurrentStatement))
					return null;

				IGConcurrentStatement cs = (IGConcurrentStatement) item;

				String segment = aPath.getSegment(i);

				IGItem childItem = cs.findChild(segment);

				if (childItem != null) {
					item = childItem;
				} else {
					if (segment != null) {
						return null;
					}
				}
			}
		} else {
			item = module;
		}

		return item;
	}

	@SuppressWarnings("unchecked")
	public HashSetArray<DUUID> findInstantiators(String aUID) {

		long mid = fZDB.getIdx(INSTANTIATORS_IDX, aUID);
		if (mid == 0) {
			return null;
		}

		return (HashSetArray<DUUID>) fZDB.load(mid);
	}

	/**
	 * Rebuild all nodes affected by changes in the given DUs.
	 * 
	 * @param aDUUIDs
	 * @return number of rebuilt nodes
	 */
	@SuppressWarnings("unchecked")
	public int rebuildNodes(HashSetArray<DUUID> aDUUIDs, IZamiaMonitor aMonitor) {

		fMonitor = aMonitor;
		ZamiaProfiler.getInstance().startTimer("IG");

		// figure out affected IG nodes,
		// delete them, invalidate parents

		HashSetArray<String> deleteNodes = new HashSetArray<String>();
		HashSetArray<String> invalidateNodes = new HashSetArray<String>();

		int n = aDUUIDs.size();
		for (int i = 0; i < n; i++) {

			DUUID duuid = aDUUIDs.get(i);

			DUUID archDUUID = fDUM.getArchDUUID(duuid);

			if (archDUUID == null) {
				logger.info("IGManager: rebuildNodes(): Warning: couldn't find architecture DUUID for %s", duuid);
				continue;
			}

			duuid = archDUUID;

			String uid = duuid.getUID();

			HashSetArray<String> signatures = (HashSetArray<String>) fZDB.getIdxObj(SIGNATURES_IDX, uid);

			if (signatures != null) {
				int m = signatures.size();
				for (int j = 0; j < m; j++) {

					String signature = signatures.get(j);

					if (deleteNodes.add(signature)) {
						logger.info("IGManager: Need to re-elaborate completeley: %s", signature);
					}
				}
			}

			long dbid = fZDB.getIdx(INSTANTIATORS_IDX, uid);
			if (dbid != 0) {
				HashSetArray<DUUID> instantiators = (HashSetArray<DUUID>) fZDB.load(dbid);

				int m = instantiators.size();
				for (int j = 0; j < m; j++) {

					DUUID instantiator = instantiators.get(j);
					String uidI = instantiator.getUID();

					HashSetArray<String> signaturesI = (HashSetArray<String>) fZDB.getIdxObj(SIGNATURES_IDX, uidI);

					if (signaturesI != null) {
						int l = signaturesI.size();
						for (int k = 0; k < l; k++) {

							String signature = signaturesI.get(k);

							if (invalidateNodes.add(signature)) {
								logger.info("IGManager: Need to re-elaborate statements: %s", signature);
							}
						}
					}
				}
			}
		}

		n = deleteNodes.size();
		for (int i = 0; i < n; i++) {

			String signature = deleteNodes.get(i);

			long dbid = fZDB.getIdx(MODULE_IDX, signature);

			if (dbid == 0) {
				continue;
			}

			IGModule module = (IGModule) fZDB.load(dbid);

			DUUID duuid = module.getDUUID();

			// remove list from instantiators list of all instantiated modules
			removeFromInstantiators(duuid, module.getStructure());

			fZDB.delIdx(MODULE_IDX, signature);
			fZDB.delIdx(INSTANTIATORS_IDX, duuid.getUID());

			fZDB.delete(dbid);
		}

		initIGBuild();

		n = invalidateNodes.size();
		for (int i = 0; i < n; i++) {

			String signature = invalidateNodes.get(i);

			long dbid = fZDB.getIdx(MODULE_IDX, signature);

			if (dbid == 0) {
				continue;
			}

			IGModule module = (IGModule) fZDB.load(dbid);

			module.updateInstantiations(deleteNodes);
		}

		// we might have deleted a toplevel node, so make sure
		// we re-build it

		BuildPath bp = fZPrj.getBuildPath();
		n = bp.getNumToplevels();

		for (int i = 0; i < n; i++) {

			if (isCanceled()) {
				logger.info("ZamiaProjectBuilder: ZamiaProjectBuilder: Canceled.");
				break;
			}

			Toplevel toplevel = bp.getToplevel(i);

			DUUID duuid = fDUM.getArchDUUID(toplevel);

			if (duuid == null) {
				logger.error("IGManager: Failed to find toplevel %s.", toplevel);
				fERM.addError(new ZamiaException(ExCat.INTERMEDIATE, true, "IGManager: failed to find toplevel " + toplevel, toplevel.getLocation()));
				continue;
			}

			String signature = IGInstantiation.computeSignature(duuid, null);

			getOrCreateIGModule(new ToplevelPath(toplevel, new PathName("")), null, duuid, signature, null, true, null);

			//			ZamiaTclInterpreter zti = fZPrj.getZTI();
			//
			//			if (zti.hasCommand(TCL_BUILD_ELABORATE_CMD)) {
			//
			//				try {
			//					String cmd = TCL_BUILD_ELABORATE_CMD + " " + duuid.getLibId() + " " + duuid.getId() + " " + duuid.getArchId();
			//					logger.info("IGManager: tcl eval '%s'", cmd);
			//					zti.eval(cmd);
			//				} catch (TclException e) {
			//					el.logException(e);
			//				}
			//			}
		}

		// finally: run the build.

		//fTotalUnits = 1000;
		fNumDone = 0;

		runIGBuild();

		ZamiaProfiler.getInstance().stopTimer("IG");

		return deleteNodes.size() + invalidateNodes.size();
	}

	@SuppressWarnings("unchecked")
	private void removeFromInstantiators(DUUID aDUUID, IGStructure aStructure) {

		int n = aStructure.getNumStatements();
		for (int i = 0; i < n; i++) {

			IGConcurrentStatement stmt = aStructure.getStatement(i);

			if (stmt instanceof IGInstantiation) {

				IGInstantiation inst = (IGInstantiation) stmt;

				DUUID duuid = inst.getChildDUUID();

				String uid = duuid.getUID();

				long mid = fZDB.getIdx(INSTANTIATORS_IDX, uid);
				if (mid != 0) {
					HashSetArray<DUUID> instantiators = (HashSetArray<DUUID>) fZDB.load(mid);

					instantiators.remove(aDUUID);
					fZDB.update(mid, instantiators);
				}

			} else if (stmt instanceof IGStructure) {

				IGStructure struct = (IGStructure) stmt;

				removeFromInstantiators(aDUUID, struct);

			}
		}
	}

	static class NodeCounter implements IGStructureVisitor {

		public int fNumNodes = 0;

		public void visit(IGStructure aStructure, PathName aPath) {
			fNumNodes++;
			//logger.debug("IGManager: Counting nodes. Node %4d %s is %s", fNumNodes, aPath, aStructure);
		}

		public int getNumNodes() {
			return fNumNodes;
		}
	}

	public int countNodes(DUUID aDUUID, int aMaxDepth) throws ZamiaException {

		logger.info("IGManager: Counting nodes in %s", aDUUID);

		String signature = IGInstantiation.computeSignature(aDUUID, null);

		IGModule module = findModule(signature);
		if (module == null) {
			return 0;
		}

		NodeCounter counter = new NodeCounter();
		module.accept(counter, aMaxDepth);

		return counter.getNumNodes();
	}

	public int countNodes(DUUID aDUUID) throws ZamiaException {
		return countNodes(aDUUID, Integer.MAX_VALUE);
	}

	public IGPackage findPackage(String aLibId, String aPkgId, SourceLocation aLocation) {

		if (ENABLE_MULTITHREADING) {
			fLock.lock();
		}

		DUUID duuid = new DUUID(LUType.Package, aLibId, aPkgId, null);

		String uid = duuid.getUID();

		IGPackage pkg = null;

		long id = fZDB.getIdx(PACKAGE_IDX, uid);
		if (id != 0) {
			pkg = (IGPackage) fZDB.load(id);
		}

		if (pkg == null) {

			DesignUnit du = null;
			try {
				du = fDUM.getDU(duuid);
			} catch (ZamiaException e) {
				el.logException(e);
			}
			if (du instanceof VHDLPackage) {

				logger.info("IGManager: building IGPackage for %s", duuid);

				VHDLPackage vpkg = (VHDLPackage) du;

				SourceLocation location = vpkg.getLocation();

				pkg = new IGPackage(duuid, location, fZDB);

				// store it right away to avoid recursion
				id = pkg.store();
				fZDB.putIdx(IGManager.PACKAGE_IDX, duuid.getUID(), id);

				vpkg.computeIG(this, pkg);
			}
		}

		if (ENABLE_MULTITHREADING) {
			fLock.unlock();
		}

		return pkg;
	}

	/*
	 * access to indexed information
	 */

	public Iterator<String> getSignalIdIterator(long aDBID) {
		return fStructSignalIdx.getKeyIterator(aDBID);
	}

	public Iterator<IGInstMapInfo> getInstIterator(long aDBID) {
		return fStructInstIdx.getValueIterator(aDBID);
	}

}
/* 
 * Copyright 2009 by the authors indicated in the @author tags. 
 * All rights reserved. 
 * 
 * See the LICENSE file for details.
 * 
 * Created by Guenter Bartsch on May 21, 2009
 */
package org.zamia.instgraph.interpreter;

import org.zamia.ErrorReport;
import org.zamia.SourceLocation;
import org.zamia.ZamiaException;
import org.zamia.instgraph.IGSubProgram;
import org.zamia.instgraph.IGSubProgram.IGBuiltin;
import org.zamia.vhdl.ast.ASTObject.ASTErrorMode;
import org.zamia.zdb.ZDB;


/**
 * 
 * @author Guenter Bartsch
 * 
 */
@SuppressWarnings("serial")
public class IGCallStmt extends IGStmt {

	private IGSubProgram fSP;

	public IGCallStmt(IGSubProgram aSP, SourceLocation aLocation, ZDB aZDB) {
		super(aLocation, aZDB);

		fSP = aSP;
	}

	@Override
	public ReturnStatus execute(IGInterpreterRuntimeEnv aRuntime, ASTErrorMode aErrorMode, ErrorReport aReport) throws ZamiaException {

		IGSubProgram sub = fSP;

		//		int n = fSPS.getNumSubPrograms();
		//		ArrayList<IGObject> mappedInterfaces = fSPS.getSubProgram(n-1).getContainer().getInterfaces();
		//		
		//		// find matching profile
		//		for (int i = 0; i<n; i++) {
		//			
		//			IGSubProgram sub = fSPS.getSubProgram(i);
		//			
		//			if (sub.getBuiltin() == null && sub.getCode() == null) {
		//				continue;
		//			}
		//			
		//			ArrayList<IGObject> interfaces = sub.getContainer().getInterfaces();
		//			
		//			boolean matched = true;
		//			
		//			int nInterfaces = interfaces.size();
		//			for (int j = 0; j<nInterfaces; j++) {
		//				
		//				IGObject intf = interfaces.get(j);
		//				
		//				// alias parameters if necessary (overloaded subprograms)
		//
		//				if (i != n-1) {
		//					IGObject mappedIntf = mappedInterfaces.get(j);
		//					aRuntime.aliasObject(intf, mappedIntf);
		//				}
		//				
		//				IGActualType interfaceType = intf.getType().computeActualType(aRuntime);
		//				
		//				IGActualConstant v = aRuntime.getObjectValue(intf);
		//				
		//				if (v==null) {
		//					throw new ZamiaException("Interpreter: IGCallStmt: Internal error, no actual value for interface "+intf+" (dbid="+intf.getDBID()+")", computeSourceLocation());
		//				}
		//				
		//				IGActualType currentType = v.getType();
		//				
		//				if (!interfaceType.isAssignmentCompatible(currentType)) {
		//					matched = false;
		//					break;
		//				}
		//			}
		//			
		//			if (matched) {

		IGInterpreterCode code = sub.getInterpreterCode();

		if (code == null) {

			IGBuiltin bi = sub.getBuiltin();
			if (bi != null) {
				return IGBuiltinOperations.execBuiltin(sub, aRuntime, computeSourceLocation(), aErrorMode, aReport);
			} else {
				throw new ZamiaException("IGCallStmt: I do not have interpreter code for " + sub);
			}
		} else {

			logger.debug("IGCallStmt: calling " + code);
			//code.dump(System.out);

			return aRuntime.call(code, aErrorMode, aReport);
		}
	}

	@Override
	public String toString() {
		return "CALL " + fSP;
	}

}
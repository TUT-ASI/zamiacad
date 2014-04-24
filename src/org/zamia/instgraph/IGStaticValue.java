/* 
 * Copyright 2009 by the authors indicated in the @author tags. 
 * All rights reserved. 
 * 
 * See the LICENSE file for details.
 * 
 * Created by Guenter Bartsch on Apr 11, 2009
 */
package org.zamia.instgraph;

import java.io.File;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;

import org.zamia.ErrorReport;
import org.zamia.SourceLocation;
import org.zamia.ZamiaException;
import org.zamia.instgraph.IGItemAccess.AccessType;
import org.zamia.instgraph.IGObject.OIDir;
import org.zamia.instgraph.IGOperationBinary.BinOp;
import org.zamia.instgraph.IGOperationUnary.UnaryOp;
import org.zamia.instgraph.IGType.TypeCat;
import org.zamia.instgraph.interpreter.IGInterpreterCode;
import org.zamia.instgraph.interpreter.IGInterpreterRuntimeEnv;
import org.zamia.instgraph.interpreter.IGPushStmt;
import org.zamia.util.HashMapArray;
import org.zamia.util.HashSetArray;
import org.zamia.vhdl.ast.VHDLNode.ASTErrorMode;
import org.zamia.zdb.ZDB;

/**
 * This represents any constant value in the interpreter or ig
 * 
 * @author Guenter Bartsch
 * Oct 2012, Valentin Tihhomirov converted conditional style into polymorphysm, http://codereview.stackexchange.com/questions/18016/replacing-conditional-with-polymorphysm-slows-down-the-performance  
 */

@SuppressWarnings("serial")
public class IGStaticValue extends IGOperation {

	public final static char BIT_0 = '0';
	public final static char BIT_1 = '1';
	public final static char BIT_Z = 'Z';
	public final static char BIT_X = 'X';
	public final static char BIT_U = 'U';
	public final static char BIT_DC = '-';
	public final static char BIT_L = 'L';
	public final static char BIT_H = 'H';
	public final static char BIT_W = 'W';

	// TODO: why do we need an ID? can we get rid of it?
	protected IGStaticValue(IGStaticValueBuilder aBuilder) {
		super(aBuilder.getType(), aBuilder.getSrc(), aBuilder.getZDB());
		fId = aBuilder.getId();
	};
	
	protected IGStaticValue(IGType aType, ZDB aZDB) {
		super(aType, null, aZDB);
	}
	
	public static class INT extends IGStaticValue {
		private BigInteger fNum;
		
		public INT(IGStaticValueBuilder aBuilder) throws ZamiaException {
			super(aBuilder);
			assert getType().isInteger() || getType().isPhysical() : "IGStaticValue.INT<init>: int/physical type expected here instead of " + getType();
			fNum = aBuilder.getNum();
			assert fNum != null : "IGStaticValue.INT<init>: int value is not defined in the builder";
		}
		
		public long getOrd() throws ZamiaException {
			return fNum.longValue();
		}
		
		public BigInteger getNum() { return fNum; }
		public int getInt() { return fNum.intValue(); }
		public int getInt(SourceLocation aSrc) {
			assert getType().isInteger() : "IGStaticValue: Integer type expected in getInt instead of " + getType();
			return fNum.intValue();
		}
		
		public String toHRString() {
			IGType type = getType();
			try {
				switch (type.getCat()) {
				case INTEGER:
					return "" + fNum;

				case PHYSICAL:
		
					IGTypeStatic st = getStaticType();
					int nUnits = st.getNumUnits(null);
		
					String baseUnit = ""; // no primary unit?
					if (nUnits > 0) {
						IGStaticValue unit = st.getUnit(0, null);
						baseUnit = unit.getId();
					}
		
					return fNum + " " + baseUnit.toLowerCase(); // units must be lowercased to conform VHDL spec
				}

			} catch (Exception e) {
				return "***ERR: " + e.getMessage();
			}

			return "***ERR: value " + fNum + " is incompliant with TYPE " + getType();
		}
		
	}
	public static class REAL extends IGStaticValue {
		private final BigDecimal fReal;
		public REAL(IGStaticValueBuilder aBuilder) throws ZamiaException {
			super(aBuilder);
			assert getType().isReal() : "IGStaticValue.REAL<init>: Real type expected here instead of " + getType();
			fReal = aBuilder.getReal();
			assert fReal != null : "IGStaticValue.REAL<init>: real value is not defined in the builder";
		}
		public BigDecimal getReal() { return fReal; }
		public BigDecimal getReal(SourceLocation aSrc) { return fReal;}
		public String toHRString() { return "" + getReal(); }

	}
	public static class ARRAY extends IGStaticValue {
		private final ArrayList<IGStaticValue> fArrayValues;
		private int fArrayOffset; // this is cached value of our_type'low
		public ARRAY(IGStaticValueBuilder aBuilder) throws ZamiaException {
			super(aBuilder);
			IGTypeStatic type = aBuilder.getType();
			assert getType().isArray() : "IGStaticValue.Array must be of array type but " + getType() + " is " + getType().getCat();
			SourceLocation location = computeSourceLocation();
			
			IGTypeStatic indexType = type.getStaticIndexType(location);

			if (!type.isUnconstrained()) {
			
				int card = (int) indexType.computeCardinality(location);
				
				fArrayOffset = (int) indexType.getStaticLow(location).getOrd();
				if (card >= 0) {
					fArrayValues = new ArrayList<IGStaticValue>(card);
					for (int i = 0; i < card; i++) {
						fArrayValues.add(aBuilder.get(i + fArrayOffset, location));
					}
					return;
				}
			}
			
			fArrayValues = null;

		}
		
		public int getNumArrayEntries(SourceLocation aLocation) { return fArrayValues.size();}
		
		@Override
		public IGStaticValue getValue(int aIdx, SourceLocation aSrc) throws ZamiaException {
			if (fArrayValues == null)
				return null;
			// boundary check
			int minIdx = fArrayOffset;
			int maxIdx = fArrayOffset + fArrayValues.size() - 1;
			if (aIdx < minIdx || aIdx > maxIdx) {
				throw new ZamiaException("IGStaticValue: getValue(): array index out of bounds: " + aIdx + " limit was " + minIdx + " to " + maxIdx, aSrc);
			}
			return fArrayValues.get(aIdx - fArrayOffset);
		}
		
		public int getArrayOffset() {
			return fArrayOffset;
		}
		
		public String toHRString() {
			StringBuilder buf = new StringBuilder();

			IGTypeStatic at = getStaticType();

			// boolean ascending = isSTAscending(at);

			if (fArrayValues != null) {
				int n = fArrayValues.size();
				if (at.isLogic() || at.isString()) {
					// buf.append("\"");
					for (int i = n - 1; i >= 0; i--) {
						IGStaticValue v = fArrayValues.get(i);
						buf.append(v != null ? v.toHRString() : "[null]");
					}
					// buf.append("\"");
				} else {

					buf.append("(");
					for (int i = 0; i < n; i++) {
						buf.append(fArrayValues.get(i));
						if (i < n - 1)
							buf.append(", ");
					}
					buf.append(")");
				}
			} else {
				buf.append(" NULL ");
			}
			return buf.toString();

		}
		
		public String toBinString() throws ZamiaException {

			IGTypeStatic type = getStaticType();

			if (!type.isLogic()) {
				return toHRString();
			}

			StringBuilder buf = new StringBuilder("B\"");
			IGTypeStatic idxType = type.getStaticIndexType(null);
			boolean ascending = idxType.isAscending();
			int n = getNumArrayEntries(null);

			for (int i = 0; i < n; i++) {
				buf.append(getValue(ascending ? i + fArrayOffset : n - i - 1 + fArrayOffset, null).toHRString());
			}

			return buf.append("\"").toString();
		}
		
	}
	
	public static class RANGE extends IGStaticValue {
		
		private final IGStaticValue fLeft, fRight, fAscending;
		
		public static class Builder {
			
			private final IGType fType;
			
			private IGStaticValue fLeft, fRight, fAscending;
			
			public Builder(IGStaticValue aPrototype) { 
				fType = aPrototype.getStaticType();
				fLeft = aPrototype.getLeft();
				fRight = aPrototype.getRight();
				fAscending = aPrototype.getAscending();
			}
			public Builder setLeft(IGStaticValue aVal) {fLeft = aVal; return this;}
			public Builder setRight(IGStaticValue aVal) {fRight = aVal; return this;}
			public Builder setAsc(IGStaticValue aVal) {fAscending = aVal; return this;}
			public RANGE buildConstant() throws ZamiaException {
				return new RANGE(fType, fLeft, fRight, fAscending);
			}
			
		}
		public RANGE(IGType aType, IGStaticValue aLeft, IGStaticValue aRight, IGStaticValue aAscending) throws ZamiaException {
			super(aType, aType.getZDB());
			assert aType.isRange() : "IGStaticValue.RANGE: Range type " + aType + " must have RANGE cathegory";
			assert aAscending != null : "IGStaticValue.RANGE: ascending attribute must not be null";
			fLeft = aLeft; 
			fRight = aRight;
			fAscending = aAscending;
		}
		
		@Override
		public String toHRString() {
			return fAscending.isTrue() ? fLeft.toHRString() + " to " + fRight.toHRString() : fLeft.toHRString() + " downto " + fRight.toHRString();
		}
		
		//TODO: get rid of the location. It was used to throw exception on range type check.  
		public IGStaticValue getAscending(SourceLocation aLocation) { return fAscending; }
		public IGStaticValue getAscending() { return fAscending; }
		public IGStaticValue getLeft(SourceLocation aLocation) { return fLeft; }
		public IGStaticValue getLeft() { return fLeft; }
		public IGStaticValue getRight(SourceLocation aLocation) {return fRight; }
		public IGStaticValue getRight() { return fRight; }

		public IGOperation getRangeLeft(SourceLocation aSrc) { return fLeft; }
		public IGOperation getRangeRight(SourceLocation aSrc) {return fRight;}
		public IGOperation getRangeAscending(IGContainer aContainer, SourceLocation aSrc) {return fAscending;}
		public IGOperation getRangeMin(IGContainer aContainer, SourceLocation aSrc) {
			return fAscending.isTrue() ? fLeft : fRight ;
		}
		public IGOperation getRangeMax(IGContainer aContainer, SourceLocation aSrc) throws ZamiaException {
			return fAscending.isTrue() ? fRight : fLeft;
		}

		
	}
	
	public static class INNER_BOOLEAN_DUPLICATE extends IGStaticValue {
		private final boolean fTruthValue;
		public INNER_BOOLEAN_DUPLICATE(boolean aTruthValue, ZDB aZDB) {
			super(null, aZDB);
			fTruthValue = aTruthValue;
		}
		
		public boolean isTrue() { return fTruthValue; }
		public String toHRString() { return "" + fTruthValue; }
		public IGType getType() { return null; }
	}
	public static class ENUM extends IGStaticValue {
		protected final int fEnumOrd;
		public ENUM(IGStaticValueBuilder aBuilder) throws ZamiaException {
			super(aBuilder);
			fEnumOrd = aBuilder.getEnumOrd();
		}
		
		public int getEnumOrd() { return fEnumOrd; }
		
		public String toHRString() {
			try {
				if (getType().isCharEnum())
					return Character.toString((char) this.getOrd());
				
				String id = getId();
				if (id != null) {
					return getId();
				}

				IGStaticValue literal = getType().getEnumLiteral(fEnumOrd, null, ASTErrorMode.EXCEPTION, null);
				return literal.getId();
			} catch (Exception e) {
				return "***ERR: " + e.getMessage();
			}

		}
		
		public long getOrd() throws ZamiaException { return fEnumOrd; }
		public boolean isTrue() { return fEnumOrd == 1; }
	}
	public static class CHAR_LITERAL extends ENUM {
		private final char fCharLiteral;
		public CHAR_LITERAL(IGStaticValueBuilder aBuilder) throws ZamiaException {
			super(aBuilder);
			fCharLiteral = aBuilder.getCharLiteral();
		}

		@Override
		public char getCharLiteral() { return fCharLiteral; }
		public boolean isCharLiteral() { return true;}
		public char computeBit() { return fCharLiteral; }
		public boolean isLogicOne() { return fCharLiteral == BIT_1;}
		public String toHRString() { return "" + fCharLiteral;}
		
	}
	public static class FILE extends IGStaticValue {

		private final File fFile;
		public FILE(IGStaticValueBuilder aBuilder) throws ZamiaException {
			super(aBuilder);
			fFile = aBuilder.getFile();
		}

		@Override
		public File getFile() { return fFile;}
		public String toHRString() {return "FILE " + fFile;}

	}
	
	public static class RECORD extends IGStaticValue {
		private final HashMapArray<String, IGStaticValue> fRecordValues;

		RECORD(IGStaticValueBuilder aBuilder) throws ZamiaException {
			super(aBuilder);
			SourceLocation location = computeSourceLocation();
			fRecordValues = new HashMapArray<String, IGStaticValue>();

			IGType type = aBuilder.getType();
			int n = type.getNumRecordFields(null);
			for (int i = 0; i < n; i++) {
				IGRecordField rf = type.getRecordField(i, location);
				String rfID = rf.getId();
				fRecordValues.put(rfID, aBuilder.get(rf, location));
			}

		}
		
		public IGStaticValue getRecordFieldValue(String aId, SourceLocation aSrc) throws ZamiaException {
			return fRecordValues.get(aId);
		}
		
		public String toHRString() {
			try {
				StringBuilder buf = new StringBuilder();
				buf.append("(");

				IGTypeStatic rt = getStaticType();

				if (rt != null) {
					int n = rt.getNumRecordFields(null);
					for (int i = 0; i < n; i++) {
						IGRecordField rf = rt.getRecordField(i, null);
						String rfID = rf.getId();
						buf.append(fRecordValues.get(rfID));
						if (i < n - 1)
							buf.append(", ");
					}
				} else {
					buf.append(" NULL ");
				}
				buf.append(")");
				return buf.toString();

			} catch (Exception e) {
				return "***ERR: " + e.getMessage();
			}
		}

	}
	
	public File getFile() { return null;}

	public IGTypeStatic getStaticType() {
		return (IGTypeStatic) getType();
	}

	/******************************************
	 * 
	 * Scalar values
	 * 
	 ******************************************/

	public BigDecimal getReal(SourceLocation aSrc) throws ZamiaException {
		throw new ZamiaException("IGStaticValue: real type values must be subclasses of REAL", aSrc);
	}

	/************************************************
	 * 
	 * Discrete
	 * 
	 ************************************************/

	public long getOrd() throws ZamiaException {
		throw new ZamiaException("IGStaticValue: getOrd() is not available in " + getClass().getSimpleName());
	}

	public char getCharLiteral() {
		return 0;
	}

	public boolean isCharLiteral() {
		return false;
	}

	/************************************************
	 * 
	 * Integer
	 * 
	 ************************************************/

	public int getInt() {
		throw new IllegalAccessError("Accessing int field of " + getClass().getSimpleName() + " : " + this);
	}

	public int getInt(SourceLocation aSrc) throws ZamiaException {
		throw new ZamiaException("IGStaticValue: you use generic static value but getInt exists only in the INT subtype", aSrc);
	}

	/************************************************
	 * 
	 * Arrays
	 * 
	 ************************************************/

	public IGStaticValue getValue(int aIdx, SourceLocation aSrc) throws ZamiaException {
		throw new ZamiaException("IGStaticValue: getValue(): this is not an array.", aSrc);
	}

	/************************************************
	 * 
	 * Records
	 * 
	 ************************************************/

	public IGStaticValue getRecordFieldValue(String aId, SourceLocation aSrc) throws ZamiaException {
		throw new ZamiaException("RuntimeException, IGStaticValue: getValue(): this is not a record.");
	}

	/**************************************************
	 * 
	 * Boolean
	 * 
	 **************************************************/

	public boolean isTrue() {
		return false;
	}

	/**************************************************
	 * 
	 * Logic
	 * 
	 **************************************************/

	public boolean isLogicOne() {
			return isTrue();
	}

	public char computeBit() {
		return BIT_U;
	}

	public static IGStaticValue generateZ(IGTypeStatic aType, SourceLocation aSrc) throws ZamiaException {
		return generateZ(aType, aSrc, false);
	}

	public static IGStaticValue generateZ(IGTypeStatic aType, SourceLocation aSrc, boolean aUseWeakest) throws ZamiaException {

		if (aType.isEnum()) {
			if (aUseWeakest && aType.getId().equals("STD_LOGIC")) {
				return aType.findEnumLiteral(BIT_Z);
			}
			return aType.getStaticLow(aSrc);
		}

		//		if (aType.isBit()) {
		//
		//			IGActualConstant literal = aType.findEnumLiteral(BIT_Z);
		//			if (literal == null) {
		//				return aType.getLow();
		//			}
		//
		//			return literal;
		//
		//		} else {

		IGStaticValueBuilder builder = new IGStaticValueBuilder(aType, null, aSrc);

		switch (aType.getCat()) {
		case ARRAY:

			IGTypeStatic elementType = aType.getStaticElementType(aSrc);

			IGStaticValue o = generateZ(elementType, aSrc, aUseWeakest);

			IGTypeStatic idxType = aType.getStaticIndexType(aSrc);
			if (!aType.isUnconstrained()) {

				int offset = builder.getArrayOffset();

				int n = (int) idxType.computeCardinality(aSrc);

				for (int i = 0; i < n; i++) {
					builder.set(i + offset, o, aSrc);
				}
			}

			break;

		case FILE:
			break;

		case INTEGER:
		case PHYSICAL:
			builder.setNum(0);
			break;

		case REAL:
			builder.setReal(0.0);
			break;

		case ENUM:
			// handled above
			break;

		case RECORD:

			int n = aType.getNumRecordFields(aSrc);
			for (int i = 0; i < n; i++) {
				IGRecordField rf = aType.getRecordField(i, aSrc);

				elementType = aType.getStaticRecordFieldType(i);

				o = generateZ(elementType, aSrc, aUseWeakest);

				//logger.info("IGStaticValue: Field #%d is %s (hash=%d), type=%s, obj=%s", i, rf, rf.hashCode(), elementType, o);

				builder.set(rf, o, aSrc);
			}
			break;

		case ACCESS:
			break;

		default:
			throw new ZamiaException("Internal error: Don't know how to generate constant value for " + aType);
		}
		//		}

		return builder.buildConstant();
	}

	/************************************************
	 * 
	 * Ranges
	 * 
	 ************************************************/

	//getAscending/Left/Right used getStaticType().isRange() check to throw exception 
	// but getRangeLeft/Right/Ascending used getStaticType().isRange() check. What is the difference?
	private class RangeException extends ZamiaException{
		RangeException(SourceLocation aLocation) {
			super("IGStaticValue: This value was not created as range. Its type, " + getType()  
					+ ", "+(getStaticType().isRange() ? "is" : "is not")+ " range cathegory.", aLocation);
		}
	}
	public IGStaticValue getAscending(SourceLocation aLocation)   throws ZamiaException { 
		throw new RangeException(aLocation);
	}

	public IGStaticValue getAscending() {
		return null;
	}

	public IGStaticValue getLeft(SourceLocation aLocation) throws ZamiaException {
		throw new RangeException(aLocation);
	}

	public IGStaticValue getRight(SourceLocation aLocation) throws ZamiaException {
		throw new RangeException(aLocation);
	}

	public IGStaticValue getLeft() {
		return null;
	}

	public IGStaticValue getRight() {
		return null;
	}

	/************************************************
	 * 
	 * Utils
	 * 
	 ************************************************/

	@Override
	public String toString() {
		return toHRString();
	}

	public String toHRString() {

		try {
			IGType type = getType();
			switch (type.getCat()) {
			case ACCESS:
				// FIXME
				return "toString is not implemented for ACCESS type";

			}
		} catch (Exception e) {
			return "***ERR: " + e.getMessage();
		}

		return "***ERR: UNKNOWN VALUE TYPE " + getType();
	}

	public static IGStaticValue computeUnary(IGStaticValue aA, UnaryOp aOp, SourceLocation aSrc, ASTErrorMode aErrorMode, ErrorReport aReport) throws ZamiaException {

		IGTypeStatic t = aA.getStaticType();
		IGStaticValue resValue = null;
		switch (t.getCat()) {
		case ARRAY:
			int card = (int) t.getStaticIndexType(aSrc).computeCardinality(aSrc);

			switch (aOp) {
			case NOT:
				IGStaticValueBuilder builder = new IGStaticValueBuilder(t, null, aSrc);
				int off = builder.getArrayOffset();
				int aOff = (int) t.getStaticIndexType(aSrc).getStaticLow(aSrc).getOrd();
				for (int i = 0; i < card; i++) {
					IGStaticValue v = computeUnary(aA.getValue(i + aOff, aSrc), aOp, aSrc, aErrorMode, aReport);
					if (v == null) {
						return null;
					}
					builder.set(i + off, v, aSrc);
				}

				resValue = builder.buildConstant();
				break;

			default:
				throw new ZamiaException("IGStaticValue: Unary operation: Sorry, operation " + aOp + " not implemented for array types yet.", aSrc);
			}
			break;

		case INTEGER:

			BigInteger num = aA.getNum();
			BigInteger res = null;

			switch (aOp) {
			case ABS:
				res = num.abs();
				break;
			case NEG:
				res = num.negate();
				break;
			default:
				throw new ZamiaException("IGStaticValue: Unsupported operation: " + aOp, aSrc);
			}

			resValue = new IGStaticValueBuilder(t, null, aSrc).setNum(res).buildConstant();

			break;

		case REAL:
		case PHYSICAL:
			BigDecimal real = aA.getReal();
			BigDecimal rres = null;

			switch (aOp) {
			case ABS:
				rres = real.abs();
				break;
			case NEG:
				rres = real.negate();
				break;
			default:
				throw new ZamiaException("IGStaticValue: Unsupported operation: " + aOp, aSrc);
			}

			resValue = new IGStaticValueBuilder(t, null, aSrc).setReal(rres).buildConstant();

			break;

		case ENUM:

			if ((t.isBool() || t.isBit()) && aOp == UnaryOp.NOT) {

				resValue = aA.isTrue() ? t.getEnumLiteral(0, aSrc, aErrorMode, aReport) : t.getEnumLiteral(1, aSrc, aErrorMode, aReport);

			} else {
				throw new ZamiaException("IGStaticValue: Sorry, not implemented: " + aOp, aSrc);
			}

			break;

		default:
			throw new ZamiaException("IGStaticValue: Sorry, not implemented: " + aOp, aSrc);
		}
		return resValue;
	}

	public static IGStaticValue computeBinary(IGStaticValue aA, IGStaticValue aB, BinOp aOp, IGTypeStatic aResType, SourceLocation aSrc, ASTErrorMode aErrorMode,
			ErrorReport aReport) throws ZamiaException {
		
		IGTypeStatic tA = aA.getStaticType();
		IGTypeStatic tB = aB.getStaticType();

		IGStaticValue resValue = null;

		// math operations

		switch (aResType.getCat()) {
		case INTEGER:

			BigInteger numR = null;

			if (tA.getCat() == TypeCat.INTEGER && tB.getCat() == TypeCat.INTEGER) {

				BigInteger numA = aA.getNum();
				BigInteger numB = aB.getNum();

				switch (aOp) {
				case ADD:
					numR = numA.add(numB);
					break;
				case SUB:
					numR = numA.subtract(numB);
					break;
				case MUL:
					numR = numA.multiply(numB);
					break;
				case DIV:
					numR = numA.divide(numB);
					break;
				case MOD:
					numR = numA.mod(numB);
					break;
				case POWER:
					numR = numA.pow(numB.intValue());
					break;
				case REM:
					numR = numA.mod(numB); // FIXME
					break;
				case MAX:
					numR = numA.compareTo(numB) > 0 ? numA : numB;
					break;
				case MIN:
					numR = numA.compareTo(numB) < 0 ? numA : numB;
					break;
				default:
					throw new ZamiaException("IGStaticValue: Unsupported integer operation: " + aOp, aSrc);
				}

			} else {

				BigDecimal numA = aA.getReal();
				BigDecimal numB = aB.getReal();
				BigDecimal decR = null;

				switch (aOp) {
				case ADD:
					decR = numA.add(numB);
					break;
				case SUB:
					decR = numA.subtract(numB);
					break;
				case MUL:
					decR = numA.multiply(numB);
					break;
				case DIV:
					decR = numA.divide(numB);
					break;
				case POWER:
					decR = numA.pow(numB.intValue());
					break;
				case MAX:
					decR = numA.compareTo(numB) > 0 ? numA : numB;
					break;
				case MIN:
					decR = numA.compareTo(numB) < 0 ? numA : numB;
					break;
				default:
					throw new ZamiaException("IGStaticValue: Unsupported floating point operation: " + aOp, aSrc);
				}

				numR = decR.toBigInteger();
			}

			resValue = new IGStaticValueBuilder(aResType, null, aSrc).setNum(numR).buildConstant();

			break;

		case REAL:
		case PHYSICAL:
			BigDecimal decA = aA.getReal();
			BigDecimal decB = aB.getReal();
			BigDecimal decR = null;

			switch (aOp) {
			case ADD:
				decR = decA.add(decB);
				break;
			case SUB:
				decR = decA.subtract(decB);
				break;
			case MUL:
				decR = decA.multiply(decB);
				break;
			case DIV:
				decR = decA.divide(decB, BigDecimal.ROUND_HALF_DOWN);
				break;
			case POWER:
				decR = decA.pow(decB.intValue());
				break;
			case MAX:
				decR = decA.compareTo(decB) > 0 ? decA : decB;
				break;
			case MIN:
				decR = decA.compareTo(decB) < 0 ? decA : decB;
				break;
			default:
				throw new ZamiaException("IGStaticValue: Unsupported floating point operation: " + aOp, aSrc);
			}

			resValue = new IGStaticValueBuilder(aResType, null, aSrc).setReal(decR).buildConstant();
			break;
		}

		if (resValue != null) {
			return resValue;
		}

		switch (tA.getCat()) {
		case ARRAY:

			int c1 = (int) tA.getStaticIndexType(aSrc).computeCardinality(aSrc);
			int c2 = (int) tB.getStaticIndexType(aSrc).computeCardinality(aSrc);

			switch (aOp) {
			case AND:
			case OR:
			case NAND:
			case NOR:
			case XOR:
			case XNOR:
				if (c1 != c2) {
					throw new ZamiaException("IGStaticValue: Arrays of different length in binary operation.", aSrc);
				}
				IGStaticValueBuilder builder = new IGStaticValueBuilder(tA, null, aSrc);
				int off = builder.getArrayOffset();
				int aOff = (int) tA.getStaticIndexType(aSrc).getStaticLow(aSrc).getOrd();
				int bOff = (int) tB.getStaticIndexType(aSrc).getStaticLow(aSrc).getOrd();
				for (int i = 0; i < c1; i++) {
					IGStaticValue v = computeBinary(aA.getValue(i + aOff, aSrc), aB.getValue(i + bOff, aSrc), aOp, tA.getStaticElementType(aSrc), aSrc, aErrorMode, aReport);
					if (v == null) {
						return null;
					}
					builder.set(i + off, v, aSrc);
				}

				resValue = builder.buildConstant();
				break;

			case EQUAL:
				if (c1 != c2) {
					return aResType.getEnumLiteral(0, aSrc, aErrorMode, aReport); // different lengths => false
				}
				for (int i = 0; i < c1; i++) {
					if (!aA.getValue(i + aA.getArrayOffset(), aSrc).equalsValue(aB.getValue(i + aB.getArrayOffset(), aSrc))) {
						return aResType.getEnumLiteral(0, aSrc, aErrorMode, aReport); // false
					}
				}
				return aResType.getEnumLiteral(1, aSrc, aErrorMode, aReport); // true

			case NEQUAL:
				if (c1 != c2) {
					return aResType.getEnumLiteral(1, aSrc, aErrorMode, aReport); // different lengths => true
				}
				for (int i = 0; i < c1; i++) {
					if (!aA.getValue(i + aA.getArrayOffset(), aSrc).equalsValue(aB.getValue(i + aB.getArrayOffset(), aSrc))) {
						return aResType.getEnumLiteral(1, aSrc, aErrorMode, aReport); // true
					}
				}
				return aResType.getEnumLiteral(0, aSrc, aErrorMode, aReport); // false

			default:
				throw new ZamiaException("IGStaticValue: Sorry, operation " + aOp + " not implemented for array types yet.", aSrc);
			}
			break;

		case INTEGER:

			BigInteger numA = aA.getNum();
			BigInteger numB = aB.getNum();

			switch (aOp) {
			case GREATER:
				resValue = numA.compareTo(numB) > 0 ? aResType.getEnumLiteral(1, aSrc, aErrorMode, aReport) : aResType.getEnumLiteral(0, aSrc, aErrorMode, aReport);
				break;
			case GREATEREQ:
				resValue = numA.compareTo(numB) >= 0 ? aResType.getEnumLiteral(1, aSrc, aErrorMode, aReport) : aResType.getEnumLiteral(0, aSrc, aErrorMode, aReport);
				break;
			case LESS:
				resValue = numA.compareTo(numB) < 0 ? aResType.getEnumLiteral(1, aSrc, aErrorMode, aReport) : aResType.getEnumLiteral(0, aSrc, aErrorMode, aReport);
				break;
			case LESSEQ:
				resValue = numA.compareTo(numB) <= 0 ? aResType.getEnumLiteral(1, aSrc, aErrorMode, aReport) : aResType.getEnumLiteral(0, aSrc, aErrorMode, aReport);
				break;
			case EQUAL:
				resValue = numA.compareTo(numB) == 0 ? aResType.getEnumLiteral(1, aSrc, aErrorMode, aReport) : aResType.getEnumLiteral(0, aSrc, aErrorMode, aReport);
				break;
			case NEQUAL:
				resValue = numA.compareTo(numB) != 0 ? aResType.getEnumLiteral(1, aSrc, aErrorMode, aReport) : aResType.getEnumLiteral(0, aSrc, aErrorMode, aReport);
				break;
			default:
				throw new ZamiaException("IGStaticValue: Unsupported operation: " + aOp, aSrc);
			}

			break;

		case REAL:

			BigDecimal realA = aA.getReal();
			BigDecimal realB = aB.getReal();

			switch (aOp) {
			case GREATER:
				resValue = realA.compareTo(realB) > 0 ? aResType.getEnumLiteral(1, aSrc, aErrorMode, aReport) : aResType.getEnumLiteral(0, aSrc, aErrorMode, aReport);
				break;
			case GREATEREQ:
				resValue = realA.compareTo(realB) >= 0 ? aResType.getEnumLiteral(1, aSrc, aErrorMode, aReport) : aResType.getEnumLiteral(0, aSrc, aErrorMode, aReport);
				break;
			case LESS:
				resValue = realA.compareTo(realB) < 0 ? aResType.getEnumLiteral(1, aSrc, aErrorMode, aReport) : aResType.getEnumLiteral(0, aSrc, aErrorMode, aReport);
				break;
			case LESSEQ:
				resValue = realA.compareTo(realB) <= 0 ? aResType.getEnumLiteral(1, aSrc, aErrorMode, aReport) : aResType.getEnumLiteral(0, aSrc, aErrorMode, aReport);
				break;
			case EQUAL:
				resValue = realA.compareTo(realB) == 0 ? aResType.getEnumLiteral(1, aSrc, aErrorMode, aReport) : aResType.getEnumLiteral(0, aSrc, aErrorMode, aReport);
				break;
			case NEQUAL:
				resValue = realA.compareTo(realB) != 0 ? aResType.getEnumLiteral(1, aSrc, aErrorMode, aReport) : aResType.getEnumLiteral(0, aSrc, aErrorMode, aReport);
				break;
			default:
				throw new ZamiaException("IGStaticValue: Unsupported operation for real types: " + aOp, aSrc);
			}

			break;

		case ENUM:

			int aOrd = (int) aA.getOrd();
			int bOrd = (int) aB.getOrd();
			switch (aOp) {
			case ADD:
				return aResType.getEnumLiteral(aOrd + bOrd, aSrc, aErrorMode, aReport);
			case SUB:
				return aResType.getEnumLiteral(aOrd - bOrd, aSrc, aErrorMode, aReport);
			case EQUAL:
				return aOrd == bOrd ? aResType.getEnumLiteral(1, aSrc, aErrorMode, aReport) : aResType.getEnumLiteral(0, aSrc, aErrorMode, aReport);
			case NEQUAL:
				return aOrd != bOrd ? aResType.getEnumLiteral(1, aSrc, aErrorMode, aReport) : aResType.getEnumLiteral(0, aSrc, aErrorMode, aReport);
			case GREATER:
				return aOrd > bOrd ? aResType.getEnumLiteral(1, aSrc, aErrorMode, aReport) : aResType.getEnumLiteral(0, aSrc, aErrorMode, aReport);
			case GREATEREQ:
				return aOrd >= bOrd ? aResType.getEnumLiteral(1, aSrc, aErrorMode, aReport) : aResType.getEnumLiteral(0, aSrc, aErrorMode, aReport);
			case LESS:
				return aOrd < bOrd ? aResType.getEnumLiteral(1, aSrc, aErrorMode, aReport) : aResType.getEnumLiteral(0, aSrc, aErrorMode, aReport);
			case LESSEQ:
				return aOrd <= bOrd ? aResType.getEnumLiteral(1, aSrc, aErrorMode, aReport) : aResType.getEnumLiteral(0, aSrc, aErrorMode, aReport);
			}

			if (tA.isBool()) {
				boolean bA = aA.isTrue();
				boolean bB = aB.isTrue();
				boolean bRes = false;

				switch (aOp) {
				case AND:
					bRes = bA && bB;
					break;
				case NAND:
					bRes = !(bA && bB);
					break;
				case NOR:
					bRes = !(bA || bB);
					break;
				case OR:
					bRes = bA || bB;
					break;
				case XOR:
					bRes = bA ^ bB;
					break;
				case XNOR:
					bRes = !(bA ^ bB);
					break;
				default:
					throw new ZamiaException("IGStaticValue: Sorry, not implemented: " + aOp, aSrc);
				}

				return tA.getEnumLiteral(bRes ? 1 : 0, aSrc, aErrorMode, aReport);

			} else if (tA.isLogic()) {

				char bA = aA.getCharLiteral();
				char bB = aB.getCharLiteral();

				char bRes = BIT_U;
				switch (aOp) {
				case AND:
					if (bA == BIT_0) {
						bRes = BIT_0;
					} else if (bA == BIT_1) {
						bRes = bB;
					} else {
						bRes = bA;
					}
					break;
				case NAND:
					if (bA == BIT_0) {
						bRes = BIT_1;
					} else if (bA == BIT_1) {
						if (bB == BIT_0) {
							bRes = BIT_1;
						} else if (bB == BIT_1) {
							bRes = BIT_0;
						} else {
							bRes = bB;
						}
					} else {
						bRes = bA;
					}
					break;

				case NOR:
					if (bA == BIT_1) {
						bRes = BIT_0;
					} else if (bA == BIT_0) {
						if (bB == BIT_0) {
							bRes = BIT_1;
						} else if (bB == BIT_1) {
							bRes = BIT_0;
						} else {
							bRes = bB;
						}
					} else {
						bRes = bA;
					}
					break;
				case OR:
					if (bA == BIT_1) {
						bRes = BIT_1;
					} else if (bA == BIT_0) {
						if (bB == BIT_0) {
							bRes = BIT_0;
						} else if (bB == BIT_1) {
							bRes = BIT_1;
						} else {
							bRes = bB;
						}
					} else {
						bRes = bA;
					}
					break;
				case XOR:
					if (bA == BIT_1) {
						if (bB == BIT_1) {
							bRes = BIT_0;
						} else if (bB == BIT_0) {
							bRes = BIT_1;
						} else {
							bRes = BIT_X;
						}
					} else if (bA == BIT_0) {
						if (bB == BIT_0) {
							bRes = BIT_0;
						} else if (bB == BIT_1) {
							bRes = BIT_1;
						} else {
							bRes = BIT_X;
						}
					} else {
						bRes = BIT_X;
					}
					break;
				case XNOR:
					if (bA == BIT_1) {
						if (bB == BIT_1) {
							bRes = BIT_1;
						} else if (bB == BIT_0) {
							bRes = BIT_0;
						} else {
							bRes = BIT_X;
						}
					} else if (bA == BIT_0) {
						if (bB == BIT_0) {
							bRes = BIT_1;
						} else if (bB == BIT_1) {
							bRes = BIT_0;
						} else {
							bRes = BIT_X;
						}
					} else {
						bRes = BIT_X;
					}
					break;
				case EQUAL:
					return bA == bB ? aResType.getEnumLiteral(1, aSrc, aErrorMode, aReport) : aResType.getEnumLiteral(0, aSrc, aErrorMode, aReport);

				case NEQUAL:
					return bA != bB ? aResType.getEnumLiteral(1, aSrc, aErrorMode, aReport) : aResType.getEnumLiteral(0, aSrc, aErrorMode, aReport);

				default:
					throw new ZamiaException("IGStaticValue: Sorry, not implemented: " + aOp, aSrc);
				}

				IGStaticValue res = tA.findEnumLiteral(bRes);
				if (res != null) {
					return res;
				}
				return tA.getStaticLow(aSrc);

			} else {
				throw new ZamiaException("IGStaticValue: Sorry, not implemented: " + aOp, aSrc);
			}

		default:
			throw new ZamiaException("IGStaticValue: Sorry, not implemented: " + aOp, aSrc);
		}
		return resValue;
	}

	public static IGStaticValue resolveStdLogic(ArrayList<IGStaticValue> aValues) throws ZamiaException {

		IGTypeStatic type = null;
		IGStaticValue resolvedValue = null;
		for (IGStaticValue value : aValues) {
			if (resolvedValue == null) {
				resolvedValue = value;
				type = value.getStaticType();
				continue;
			}
			if (resolvedValue instanceof CHAR_LITERAL) {
				char curC = resolvedValue.getCharLiteral();
				char newC = value.getCharLiteral();

				if (curC == BIT_U || newC == BIT_U) {
					curC = BIT_U;
				} else {
					switch (curC) {
						case BIT_X:
							break;
						case BIT_0:
							if (newC == BIT_X || newC == BIT_1 || newC == BIT_DC) {
								curC = BIT_X;
							}
							break;
						case BIT_1:
							if (newC == BIT_X || newC == BIT_0 || newC == BIT_DC) {
								curC = BIT_X;
							}
							break;
						case BIT_Z:
							curC = newC;
							if (newC == BIT_DC) {
								curC = BIT_X;
							}
							break;
						case BIT_W:
							if (newC == BIT_X || newC == BIT_DC) {
								curC = BIT_X;
							} else
							if (newC == BIT_0 || newC == BIT_1) {
								curC = newC;
							}
							break;
						case BIT_L:
							if (newC == BIT_X || newC == BIT_DC) {
								curC = BIT_X;
							} else
							if (newC == BIT_0 || newC == BIT_1 || newC == BIT_W) {
								curC = newC;
							} else
							if (newC == BIT_H) {
								curC = BIT_W;
							}
							break;
						case BIT_H:
							curC = newC;
							if (newC == BIT_Z){
								curC = BIT_H;
							} else
							if (newC == BIT_L){
								curC = BIT_W;
							} else
							if (newC == BIT_DC){
								curC = BIT_X;
							}
							break;
						case BIT_DC:
							curC = BIT_X;
							break;
						default:
							throw new ZamiaException("IGStaticValue: resolution of charLiteral " + curC + " is not supported");
					}
				}
				resolvedValue = type.findEnumLiteral(curC);

			} else {
				throw new ZamiaException("IGStaticValue: resolution of " + type + " is not supported");
			}
		}
		return resolvedValue;
	}

//	public static int getInt(String aString) {
//		return (int) getLong(aString);
//	}
//
	public static long getLong(char aChar) {
		if (aChar == BIT_1)
			return 1l;
		return 0l;
	}

	public BigInteger getNum() {
		return null;
	}

	//TODO: push down into the REAL. Other values must not have this method
	public BigDecimal getReal() {
		return null;
	}

	public int getArrayOffset() {
		return -1;
	}

	public int getEnumOrd() {
		return -1;
	}

	//	public IGObject getReferencedObject() {
	//		return (IGObject) getZDB().load(fReferencedObjectDBID);
	//	}

	public static String convert(int aChar, int aWidth) {
		return convert(new BigInteger(String.valueOf(aChar)), aWidth);
	}

	public static String convert(long aNum, int aWidth) {
		return convert(new BigInteger(String.valueOf(aNum)), aWidth);
	}

	// width can be 0 means that we generate the minimum length
	// string that can hold the given constant

	public static String convert(BigInteger aNum, int aWidth) {

		int width = aWidth;
		if (width == 0) {
			width = (int) Math.round(Math.floor(Math.log(aNum.doubleValue()) / Math.log(2))) + 1;
		}

		StringBuffer ret = new StringBuffer(aWidth);
		for (int i = width - 1; i >= 0; i--) {
			if (aNum.testBit(i))
				ret.append(BIT_1);
			else
				ret.append(BIT_0);
		}
		// System.out.println ("Converted "+v_+" to "+ret);
		return ret.toString();
	}

	public static int adjustIdx(int idx, boolean asc, int num, int offset) {
		return asc ? (num - 1) - (idx - offset) + offset : idx;
	}

	public String getId() {
		return fId;
	}

	@Override
	public IGItem getChild(int aIdx) {
		return null;
	}

	@Override
	public int getNumChildren() {
		return 0;
	}

	@Override
	public void computeAccessedItems(boolean aLeftSide, IGItem aFilterItem, AccessType aFilterType, int aDepth, HashSetArray<IGItemAccess> aAccessedItems) {
		// FIXME: access to constants/literals
	}

	@Override
	public void generateCode(boolean aFromInside, IGInterpreterCode aCode) throws ZamiaException {
		aCode.add(new IGPushStmt.STATIC_VALUE(this, computeSourceLocation(), getZDB()));
	}

	@Override
	public OIDir getDirection() throws ZamiaException {
		return OIDir.NONE;
	}

	@Override
	public int getNumOperands() {
		return 0;
	}

	@Override
	public IGOperation getOperand(int aIdx) {
		return null;
	}

	@Override
	public IGStaticValue computeStaticValue(IGInterpreterRuntimeEnv aEnv, ASTErrorMode aErrorMode, ErrorReport aReport) throws ZamiaException {
		return this;
	}

	@Override
	public IGOperation getRangeLeft(SourceLocation aSrc) throws ZamiaException {
		throw new RangeException(aSrc);
	}

	public IGOperation getRangeRight(SourceLocation aSrc) throws ZamiaException {
		throw new RangeException(aSrc);
	}

	public IGOperation getRangeAscending(IGContainer aContainer, SourceLocation aSrc) throws ZamiaException {
		throw new RangeException(aSrc);
	}

	public IGOperation getRangeMin(IGContainer aContainer, SourceLocation aSrc) throws ZamiaException {
		throw new RangeException(aSrc);
	}

	public IGOperation getRangeMax(IGContainer aContainer, SourceLocation aSrc) throws ZamiaException {
		throw new RangeException(aSrc);
	}

	@Override
	public IGType getType() {
		return super.getType();
	}

	public int getNumArrayEntries(SourceLocation aLocation) throws ZamiaException {
		new Throwable().printStackTrace();
		throw new ZamiaException("IGStaticValue: Internal interpreter error: not array: " + this + ", type: " + getType(), aLocation);
	}

	public boolean equalsValue(IGStaticValue aV) throws ZamiaException {

		IGTypeStatic t1 = getStaticType();
		IGTypeStatic t2 = aV.getStaticType();

		TypeCat cat1 = t1.getCat();
		TypeCat cat2 = t2.getCat();

		if (cat1 != cat2)
			return false;

		if (t1.isScalar()) {

			if (isCharLiteral() != aV.isCharLiteral()) {
				return false;
			}

			if (isCharLiteral()) {
				return getCharLiteral() == aV.getCharLiteral();
			}

			return getOrd() == aV.getOrd();
		}

		if (t1.isArray()) {

			int o1 = getArrayOffset();
			int o2 = aV.getArrayOffset();

			int n1 = getNumArrayEntries(null);
			int n2 = aV.getNumArrayEntries(null);
			if (n1 != n2 || o1 != o2) {
				return false;
			}

			for (int i = 0; i < n1; i++) {
				IGStaticValue v1 = getValue(i + o1, null);
				IGStaticValue v2 = aV.getValue(i + o2, null);
				if (!v1.equalsValue(v2)) {
					return false;
				}
			}
			return true;
		}

		if (t1.isRecord()) {

			int n1 = t1.getNumRecordFields(null);
			int n2 = t2.getNumRecordFields(null);

			if (n1 != n2) {
				return false;
			}

			for (int i = 0; i < n1; i++) {

				IGRecordField rf1 = t1.getRecordField(i, null);
				IGRecordField rf2 = t2.getRecordField(i, null);

				String id1 = rf1.getId();
				String id2 = rf2.getId();

				if (!id1.equals(id2)) {
					return false;
				}

				IGStaticValue v1 = getRecordFieldValue(id1, null);
				IGStaticValue v2 = aV.getRecordFieldValue(id2, null);
				if (!v1.equalsValue(v2)) {
					return false;
				}
			}
			return true;
		}

		// FIXME: implement
		throw new ZamiaException("IGStaticValue: equalsValue(): sorry, not implemented for " + cat1 + " types.");
	}

	public long toLongNumber() throws ZamiaException {
		long v = 0;

		String str = toHRString();

		//		IGTypeStatic t = getStaticType();
		//		boolean asc = isSTAscending(t);

		int l = str.length();
		for (int i = 0; i < l; i++) {
			//char c = str.charAt(asc ? l-i-1 : i);
			char c = str.charAt(i);

			switch (c) {
			case BIT_0:
				v = v * 2l;
				break;
			case BIT_1:
				v = v * 2l + 1l;
				break;
			default:
				v = v * 2l;
			}
		}

		return v;
	}

	public String toHexString() {

		if (!getType().isLogic()) {
			return toHRString();
		}

		try {
			long l = toLongNumber();

			
			int len = (this instanceof ARRAY) ? (((ARRAY)this).fArrayValues.size() + 3) / 4 : 1;
			if (len < 1) {
				len = 1;
			}

			String hs = Long.toHexString(l).toUpperCase();
			int hsl = hs.length();
			if (hsl < len) {
				StringBuilder buf = new StringBuilder();
				for (int i = hsl; i < len; i++) {
					buf.append("0");
				}
				buf.append(hs);
				hs = buf.toString();
			}

			return "X\"" + hs + "\"";
		} catch (Throwable t) {
			return toHRString();
		}
	}

	public String toDecString() {

		if (!getType().isLogic()) {
			return toHRString();
		}

		try {
			long l = toLongNumber();
			return "" + l;
		} catch (ZamiaException e) {
			return toHRString();
		}
	}

	public String toOctString() {

		if (!getType().isLogic()) {
			return toHRString();
		}

		try {
			long l = toLongNumber();
			return "O\"" + Long.toOctalString(l) + "\"";
		} catch (ZamiaException e) {
			return toHRString();
		}
	}

	public String toBinString() throws ZamiaException {
		return toHRString();
	}
}

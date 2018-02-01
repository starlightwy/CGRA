package functionalunit;

import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;

import converter.classloader.test.Pair;
import dataContainer.ByteCode;
import dataContainer.Invokation;
import dataContainer.MethodDescriptor;
import dataContainer.SynthesizedKernelDescriptor;
import exceptions.AmidarSimulatorException;
import amidar.axtLoader.AXTDataSection;
import amidar.axtLoader.AXTFile;
import amidar.axtLoader.AXTHeader;
import amidar.axtLoader.AXTLoader;
import amidar.axtLoader.AXTTableSection;
import tracer.Trace;
import tracer.TraceManager;
import functionalunit.FunctionalUnit.State;
//import functionalunit.heap.HandleTableCache;
import functionalunit.cache.Memory;
import functionalunit.cache.coherency.CoherenceController;
import functionalunit.opcodes.FrameStackOpcodes;
import functionalunit.opcodes.TokenMachineOpcodes;
import functionalunit.tables.ClassTableEntry;
import functionalunit.tables.ConstantPoolEntry;
import functionalunit.tables.ExceptionTableEntry;
import functionalunit.tables.ImplementedInterfacesTableEntry;
import functionalunit.tables.InterfaceTableEntry;
import functionalunit.tables.MethodTableEntry;
import functionalunit.tables.SimpleTableCache;
import functionalunit.tables.TableCache;
import functionalunit.tokenmachine.BranchPredictor;
import functionalunit.tokenmachine.ClassController;
import functionalunit.tokenmachine.DecodeStage;
import functionalunit.tokenmachine.DistributionStage;
import functionalunit.tokenmachine.FIFO;
import functionalunit.tokenmachine.FetchStage;
import functionalunit.tokenmachine.InstructionCache;
import functionalunit.tokenmachine.Profiler;
import functionalunit.tokenmachine.SimpleInstructionCache;
import functionalunit.tokenmachine.KernelProfiler;

/**
 * Implementing the TokenMachine
 * TODO: HW Profiler
 * TODO: Exceptions
 * TODO: GC
 * TODO: Thread Scheduler
 * @author jung
 *
 */
public class TokenMachine extends FunctionalUnit<TokenMachineOpcodes> {
	
	// APPLICATION DESCRIPTION
	private int arrayTypeOffset = 0;
	private int interfaceOffset = 0;
	private int actualApplicationStart = 0;
	public boolean startedActualApplication = false;

	
	
	// VARIABLES STORING THE CURRENT CONTEXT
//	protected int programCounter = 0;
	private int applicationBaseAddress = 0;	
	private int currentMethodBaseAddress = 0;
//	public int instructionMemoryAddress =  0;//applicationBaseAddress + currentMethodBaseAddress + programCounter;
	private int currentAMTI = 0;
//	private boolean endOfCode = false;


	// VARIABLES STORING INTERNAL STATUS SIGNALS
	protected boolean isJump;
	private boolean executedJump;
	private boolean executedBranch;
	protected int bytecodeOffset; 
	protected int sendConstantCount;
	protected int sentConstants;
	private int parameter;
	private byte currentInstruction;
	private DecodeState decodeState = DecodeState.IDLE;	
	private int tokenState = 0;

//	protected int loopIterations;
//	protected boolean loopIterationsValid;


	// VARIABLES STORING INTERMEDIATE VALUES FOR METHOD INVOKATION
	private int methodRIMTI = 0;
	private int methodIOLI = 0;
	private int methodRMTI = 0;
	private int methodAMTI = 0;
	private int methodArgs = 0;
	private int methodMaxLocals = 0;
	private int methodBaseAddress = 0;
	private int interfaceTableIndex = 0;
	
	// FOR EXCEPTIONS
	private int exceptionTableIndexCounter = 0;
	private int exceptionTableIndex = 0;
	private int exceptionLength = 0;
	private int start = 0;
	private int stop = 0;
	private int expectedCTI = 0;
	private boolean handlingException = false;
	ExceptionTableEntry entry;


	// THE CACHES OF THE SYSTEM
	private InstructionCache instructionCache;
	private ClassController classController;
	private TableCache<MethodTableEntry> methodTable;
	private TableCache<ConstantPoolEntry> constantPool;
	private TableCache<ExceptionTableEntry> exceptionTable;
	private TableCache<InterfaceTableEntry> interfaceTable;


	// FUNCTIONAL UNITS
	protected IALU ialu;
	protected IMUL imul;
	protected IDIV idiv;
	protected LALU lalu;
	protected FPU fpu;
	protected FDIV fdiv;
	protected ObjectHeap heap;
	protected FrameStack frameStack;
	protected TokenMachine tokenMachine;
	protected CGRA cgra;
	protected Scheduler scheduler;
	
	
	// DECODE PIPELINE
	private FIFO parameterFifoParameter;
	private FIFO parameterFifoNrConstants;
	private BranchPredictor branchPredictor;
	private boolean tookBranch;
	private FetchStage fetchStage;
	private DecodeStage decodeStage;
	private DistributionStage distributionStage;
	private int newAddress;
	
	// ENERGY VALUES
	double bytecodeEnergy = 0;
	
	// PROFILER FOR SYNTHESIS
	private Profiler profiler;
	
	// PROFILER FOR DEBUGGING
	private KernelProfiler kernelProfiler;
	
	// DATA FOR SYNTHESIS
	private int[] liveInOutMemory;
	private SynthesizedKernelDescriptor[] kernelTable;
	private SynthesizedKernelDescriptor currentKernel;
	private int synthConstPointer;
	
	private ArrayList<Invokation> invokationHistory; // Used as estimator for speculative method inlining
	private int invokationHistoryLength = 1024;
	
	// DEBUG INFO
	private String[] methodNames; 	// Only for Debugging, native Methods and Synthesis report
	Trace methodTracer;
	String methodPrefix = "";		// This has to be handled differently when multi threadding is supported
	
	// NATIVE METHODS
	private Map<String,Boolean> nativeMethods;
	Trace systemOutTracer;
	Trace heapTracer;

	/**
	 * Creates a new Tokenmachine with given configuration file and a tracemanager
	 * @param configFile the path to the config file
	 * @param traceManager the acutal trace manager
	 */
	public TokenMachine(String configFile, TraceManager traceManager, int benchmarkScale) {
		super(TokenMachineOpcodes.class, configFile, traceManager);
		bytecodeEnergy = (Double)jsonConfig.get("bytecodeEnergy");
		profiler = new Profiler();
		kernelProfiler = new KernelProfiler();
		liveInOutMemory = new int [2048];						// TODO
		kernelTable = new SynthesizedKernelDescriptor[64];  // TODO
		for(int i = 0; i < kernelTable.length; i++){
			kernelTable[i] = new SynthesizedKernelDescriptor();
			kernelTable[i].setContextPointer(255);
		}
//		kernelTable[0] = new SynthesizedKernelDescriptor();
//		kernelTable[0].setContextPointer(255);
		methodTracer = traceManager.getf("methods");
		
		invokationHistory = new ArrayList<Invokation>();
		
		systemOutTracer = traceManager.getf("system");
		heapTracer = traceManager.getf("heap");
		systemOutTracer.prefixed(false);
		
		branchPredictor = new BranchPredictor();
		instructionCache = new SimpleInstructionCache();
		
		fetchStage = new FetchStage(instructionCache, 16); // TODO depth festlegen
		
		decodeStage = new DecodeStage(fetchStage,16,branchPredictor);
		distributionStage = new DistributionStage(decodeStage, profiler);
		
		parameterFifoNrConstants = decodeStage.getParameterFifoNrConstants();
		parameterFifoParameter = decodeStage.getParameterFifoParameter();
		
		this.benchmarkScale = benchmarkScale;
	}

	/**
	 * Sets all FUs so that the tokenmachine can send token to all FUs
	 * Corresponds to the token distribution network
	 * @param ialu the integer ALU
	 * @param falu the floating point ALU
	 * @param heap the heap memory
	 * @param frameStack the framestack
	 */
	public void setFUs( IALU ialu,
			 IMUL imul,
			 IDIV idiv,
			 LALU lalu,
			 FPU fpu,
			 FDIV fdiv,
			 ObjectHeap heap,
			 FrameStack frameStack,
			 TokenMachine tokenMachine,
			 CGRA cgra,
			 Scheduler scheduler){
		this.ialu = ialu;
		this.imul = imul;
		this.idiv = idiv;
		this.lalu = lalu;
		this.fpu = fpu;
		this.fdiv = fdiv;
		this.heap = heap;
		this.frameStack = frameStack;
		this.cgra = cgra;
		this.tokenMachine = this;
		this.scheduler = scheduler;

		distributionStage.setFUs(ialu, imul, idiv, lalu, fpu, fdiv, heap, frameStack, tokenMachine, cgra, scheduler);
	}

	/**
	 * Initializes all memories with data loaded from the AXT file.
	 * This method has to be called before a simulation is executed.
	 * Corresponds to a bootloader.
	 */
	public void initTables(AXTLoader axtLoader){
		//
		nativeMethods = new LinkedHashMap<String,Boolean>();
		nativeMethods.put("java/io/PrintStream.println()V", false);
		nativeMethods.put("java/io/PrintStream.print(I)V", false);
		nativeMethods.put("java/io/PrintStream.print(J)V", false);
		nativeMethods.put("java/io/PrintStream.print(D)V", false);
		nativeMethods.put("java/io/PrintStream.print(F)V", false);
		nativeMethods.put("java/io/PrintStream.print(Z)V", false);
		nativeMethods.put("java/io/PrintStream.print(S)V", false);
		nativeMethods.put("java/io/PrintStream.print(B)V", false);
		nativeMethods.put("java/io/PrintStream.print(C)V", false);
		nativeMethods.put("java/io/PrintStream.print([C)V", false);
		nativeMethods.put("java/io/PrintStream.print(Ljava/lang/String;)V", false);
		nativeMethods.put("java/io/PrintStream.flush()V", false);
		nativeMethods.put("java/io/FileInputStream.read()I", true);
		nativeMethods.put("java/io/FileInputStream.read([BII)I", true);
		nativeMethods.put("java/io/File.lengthA()I",true);
		nativeMethods.put("java/io/File.lengthB()I",true);
		nativeMethods.put("java/lang/System.arraycopyN(Ljava/lang/Object;ILjava/lang/Object;II)V", false);
		nativeMethods.put("java/lang/Float.floatToIntBits(F)I", true);
		nativeMethods.put("java/lang/Float.intBitsToFloat(I)F", true);
		nativeMethods.put("java/lang/System.currentTimeMillisLow()I",true);
		nativeMethods.put("java/lang/System.currentTimeMillisHigh()I",true);
		nativeMethods.put("java/lang/System.nanoTimeLow()I",true);
		nativeMethods.put("java/lang/System.nanoTimeHigh()I",true);
		nativeMethods.put("graph500/Kernel2InputParserSparseParallel.readLine(Ljava/io/FileInputStream;[C)V", false);
		nativeMethods.put("graph500/Kernel2InputParserSparseParallel.readIJ(Ljava/io/FileInputStream;[I[II)V", false);
		nativeMethods.put("de/amidar/AmidarSystem.invalidateFlushAllCaches()V", false);
		nativeMethods.put("de/amidar/AmidarSystem.invalidateFlushAllCaches()V", false);
		nativeMethods.put("de/amidar/cacheBench/CacheBenchParameters.getBenchmarkScale()I", true);
		
		//
		TableCache<ClassTableEntry> classTable = new SimpleTableCache<ClassTableEntry>();
		TableCache<ImplementedInterfacesTableEntry> implementedInterfacesTable = new SimpleTableCache<ImplementedInterfacesTableEntry>();
		classController = new ClassController(classTable, implementedInterfacesTable);

		methodTable = new SimpleTableCache<MethodTableEntry>();
		constantPool = new SimpleTableCache<ConstantPoolEntry>();
		exceptionTable = new SimpleTableCache<ExceptionTableEntry>();
		interfaceTable = new SimpleTableCache<InterfaceTableEntry>();

		

		AXTFile axtFile = axtLoader.getAxtFile();
		AXTDataSection axtDataSection = axtFile.getDataSec(); // Contains Code + Constants
		AXTTableSection axtTableSection = axtFile.getTabSec();// Contains Tables
		AXTHeader axtHeader = axtFile.getHeader();


		/// MAKING DEEP COPY OF AXT - SO WE CAN REUSE AXTLoader

		//LOAD CODE
		byte [] codeMemoryOrigin = axtDataSection.getBytecode();
		byte [] codeMemory = new byte[codeMemoryOrigin.length + 100]; // +100 so that prefretching does not result in ArrayIndexOutof bound Exceptions
		System.arraycopy(codeMemoryOrigin, 0, codeMemory, 0, codeMemoryOrigin.length);

		instructionCache.initMemory(codeMemory);
		decodeStage.setMemory(codeMemory);
		arrayTypeOffset = axtHeader.getArrayTypeOffset(0);
		interfaceOffset = axtHeader.getInterfaceOffset(0);
		classController.setArrayTypeOffset(arrayTypeOffset);
		classController.setInterfaceOffset(interfaceOffset);
		
		// find start of actual code - everything before is static initializer
		int index = 0;
		while(codeMemory[index + ByteCode.getParamCount(codeMemory[index])+1] != ByteCode.GOTO){
			index += ByteCode.getParamCount(codeMemory[index])+1 ;
		}
		actualApplicationStart = index -3;
//		System.err.println("ACTUAL START: " + actualApplicationStart);
		
		//LOAD CLASSTABLE
		int classTableSize = axtTableSection.getClassTableSize();
		ClassTableEntry [] cMemory = new ClassTableEntry[classTableSize];
		for(int i = 0; i< classTableSize; i++){ //TODO getters of axtTableSection are quite ineffective....
			int[] data = new int[6];
			data[ClassTableEntry.CLASSSIZE] = axtTableSection.classTableGetObjectSize(i);
			data[ClassTableEntry.FLAGS] = axtTableSection.classTableGetClassFlags(i);
			data[ClassTableEntry.IMPL_INTERFACE_TABLE_REF] = axtTableSection.classTableGetImplInterfaceTableRefOffset(i);
			data[ClassTableEntry.INTERFACE_TABLE_REF]  = axtTableSection.classTableGetInterfaceTableRefOffset(i);
			data[ClassTableEntry.METHOD_TABLE_REF] = axtTableSection.classTableGetMethodTableRef(i);
			data[ClassTableEntry.SUPER_CTI] = axtTableSection.classTableGetSuperCTI(i);
			cMemory[i] = new ClassTableEntry(data);
		}
		classTable.initMemory(cMemory);

		
		//LOAD METHODTABLE
		int nrOfMethods = (int)(axtHeader.getNumberOfMethods() + axtHeader.getNumberOfStaticMethods());
		MethodTableEntry [] mMemory = new MethodTableEntry[nrOfMethods];	
		for(int i = 0; i < nrOfMethods; i++){
			int[] data = new int[8];
			data[MethodTableEntry.CODE_LENGTH] = axtTableSection.methodTableGetCodeLength(i);
			data[MethodTableEntry.CODE_REF] = (int)axtTableSection.methodTableGetCodeRef(i);
			data[MethodTableEntry.EXCEPTION_TABLE_LENGTH] = axtTableSection.methodTableGetExceptionTableLength(i);
			data[MethodTableEntry.EXCEPTION_TABLE_REF] = axtTableSection.methodTableGetExceptionTableRef(i);
			data[MethodTableEntry.FLAGS] = axtTableSection.methodTableGetMethodFlags(i);
			data[MethodTableEntry.MAX_LOCALS] = axtTableSection.methodTableGetMaxLocals(i);
			data[MethodTableEntry.MAX_STACK] = axtTableSection.methodTableGetMaxStack(i);
			data[MethodTableEntry.NUMBER_ARGS] = axtTableSection.methodTableGetNumArgs(i);
			mMemory[i] = new MethodTableEntry(data);

		}
		methodTable.initMemory(mMemory);

		
		//LOAD CONSTANT POOL
		int nrOfConstants = axtDataSection.getConstantPoolSize();
		ConstantPoolEntry [] constMemory = new ConstantPoolEntry[nrOfConstants];
		for(int i = 0; i < nrOfConstants; i++){
			int[] data = new int[1];
			data[0] = axtDataSection.getConstantPoolEntry(i);
			constMemory[i] = new ConstantPoolEntry(data);
		}
		constantPool.initMemory(constMemory);

		
		//LOAD EXCEPTIONTABLE
		int nrOfExceptions = axtTableSection.getExceptionTableSize();
		ExceptionTableEntry [] eMemory = new ExceptionTableEntry[nrOfExceptions];
		for(int i = 0; i < nrOfExceptions; i++){
			int[] data = new int [4];
			data[ExceptionTableEntry.CATCH_TYPE_CTI] = axtTableSection.exceptionTableGetCatchType(i);
			data[ExceptionTableEntry.END] = axtTableSection.exceptionTableGetEndPC(i);
			data[ExceptionTableEntry.PC_HANDLER] = axtTableSection.exceptionTableGetHandlerPC(i);
			data[ExceptionTableEntry.START] = axtTableSection.exceptionTableGetStartPC(i);

			eMemory[i] = new ExceptionTableEntry(data);
		}
		exceptionTable.initMemory(eMemory);

		
		//LOAD INTERFACETABLE
		int interfaceTableSize = axtTableSection.getInterfaceTableSize();
		InterfaceTableEntry [] iMemory = new InterfaceTableEntry[interfaceTableSize];
		for(int i = 0; i < interfaceTableSize; i++){
			int[] data = new int[1];
			data[0] = axtTableSection.interfaceTableGetMethodOffset(i);
			iMemory[i] = new InterfaceTableEntry(data);
		}
		interfaceTable.initMemory(iMemory);

		
		//LOAD IMPL. INTERFACES TABLE
		int nrOfImplementedInterfaces = axtTableSection.getImplementedInterfacesSize();
		ImplementedInterfacesTableEntry[] iiMemory = new ImplementedInterfacesTableEntry[nrOfImplementedInterfaces];
		int entries = axtHeader.getImplementedInterfacesEntrySize()*8;
		for(int i = 0; i< nrOfImplementedInterfaces; i++){
			int[] data = new int[entries];
			int value = axtTableSection.getImplementedInterfaces(i);
			for(int j = entries-1; j >= 0; j--){
				data[j] = value % 2;
				value = value>>>1;
			}
			iiMemory[i] = new ImplementedInterfacesTableEntry(data);
		}
		implementedInterfacesTable.initMemory(iiMemory);
	}


	@Override
	public int getNrOfInputports() {
		return 4;
	}



	@Override
	public boolean executeOp(TokenMachineOpcodes op) {
		executedJump = false;
		executedBranch = false;
//		System.out.println("TM exec " + op);
		switch(op){
		case BRANCH_IF_LE:
			executedBranch = true;
			profiler.jump(distributionStage.getCurrentAddress(), input[OPERAND_A_LOW], currentAMTI);
			if(input[OPERAND_B_LOW] != 1){
				tookBranch = true;
			}else{
				tookBranch = false;
			}
			setResultAck(true);
			break;
		case BRANCH_IF_GT:
			executedBranch = true;
			profiler.jump(distributionStage.getCurrentAddress(), input[OPERAND_A_LOW], currentAMTI);
			if(input[OPERAND_B_LOW] == 1){
				tookBranch = true;
			}else{
				tookBranch = false;
			}
//			System.out.println("IF_GT " + tookBranch);
			setResultAck(true);
			break;
		case BRANCH_IF_GE:
			executedBranch = true;
			profiler.jump(distributionStage.getCurrentAddress(), input[OPERAND_A_LOW], currentAMTI);
			if(input[OPERAND_B_LOW] != -1){
				tookBranch = true;
			}else{
				tookBranch = false;
			}
			setResultAck(true);
			break;
		case BRANCH_IF_LT:
			executedBranch = true;
			profiler.jump(distributionStage.getCurrentAddress(), input[OPERAND_A_LOW], currentAMTI);
			if(input[OPERAND_B_LOW] == -1){
				tookBranch = true;
			}else{
				tookBranch = false;
			}
			setResultAck(true);
			break;
		case BRANCH_IF_NE:
			executedBranch = true;
			profiler.jump(distributionStage.getCurrentAddress(), input[OPERAND_A_LOW], currentAMTI);
			if(input[OPERAND_B_LOW] != 0){
				tookBranch = true;
			}else{
				tookBranch = false;
			}
			setResultAck(true);
			break;
		case BRANCH_IF_EQ:
			executedBranch = true;
			profiler.jump(distributionStage.getCurrentAddress(), input[OPERAND_A_LOW], currentAMTI);
			if(input[OPERAND_B_LOW] == 0){
				tookBranch = true;
			}else{
				tookBranch = false;
			}
			setResultAck(true);
			break;
		case BRANCH:
			executedJump = true;
			profiler.jump(distributionStage.getCurrentAddress(), input[OPERAND_A_LOW], currentAMTI);
			newAddress = distributionStage.getAddressOfJump() + input[OPERAND_A_LOW];
			setResultAck(true);
			break;
		case CLASSSIZE:
			if(tokenState == 0){
				classController.requestClassInfo(input[OPERAND_A_LOW]);
				tokenState = 1;
				return false;
			} else {
				if(classController.requestClassInfo(input[OPERAND_A_LOW])){
					output[RESULT_LOW]= classController.getClassSize();
					setOutputValid(RESULT_LOW);
				} else {
					return false;
				}
			}
			break;
		case SENDBYTECODE_1:
//			switch(tokenState){
//			case 0:
				if(fetchParameter()){
//					tokenState = 1;
					output[RESULT_LOW]= (byte)getParameterByte(1);
				setOutputValid(RESULT_LOW);
				tokenState = 0;
//				System.out.println("BC1 " + output[RESULT_LOW]);
				sentConstants++;
				} else
				return false;
//			case 1:
//
//			}
			break;
		case SENDBYTECODE_2:
//			switch(tokenState){
//			case 0:
				if(fetchParameter()){
//					tokenState = 1;
					output[RESULT_LOW]= (byte)getParameterByte(2);
					setOutputValid(RESULT_LOW);
//					System.out.println("BC2 " + output[RESULT_LOW]);
					tokenState = 0;
					sentConstants++;
				} else
				return false;
//			case 1:
//				
//			}
			break;
		case SENDBYTECODE_3:
//			switch(tokenState){
//			case 0:
				if(fetchParameter()){
//					tokenState = 1;
					output[RESULT_LOW]= (byte)getParameterByte(3);
					setOutputValid(RESULT_LOW);
					tokenState = 0;
					sentConstants++;
				} else
				return false;
//			case 1:
//
//			}
			break;
		case SENDBYTECODE_1_2:
//			switch(tokenState){
//			case 0:
				if(fetchParameter()){
//					tokenState = 1;
					output[RESULT_LOW] = (short)((getParameterByte(1)<<8)|getParameterByte(2));
//					System.out.println("BC12 " + output[RESULT_LOW]);
					setOutputValid(RESULT_LOW);
					tokenState = 0;
					sentConstants++;
				} else
				return false;
//			case 1:
//
//			}
			break;
		case SENDBYTECODE_3_4:
//			switch(tokenState){
//			case 0:
				if(fetchParameter()){
//					tokenState = 1;
					output[RESULT_LOW] = (short)((getParameterByte(3)<<8)|getParameterByte(4));
					setOutputValid(RESULT_LOW);
					tokenState = 0;
					sentConstants++;
				} else
				return false;
//			case 1:
//
//			}
			break;
		case SENDBYTECODE_1_2_3_4:
//			switch(tokenState){
//			case 0:
				if(fetchParameter()){
//					tokenState = 1;
					output[RESULT_LOW]  = (getParameterByte(1)<<24)|(getParameterByte(2)<<16)|(getParameterByte(3)<<8)|getParameterByte(4);
					setOutputValid(RESULT_LOW);
					tokenState = 0;
					sentConstants++;
				} else
				return false;
//			case 1:
//
//			}

			break;
		case LOAD_ARG_IOLI_RIMTI:
			methodArgs = (input[OPERAND_A_LOW]>>26)&0x3F;
			methodIOLI = (input[OPERAND_A_LOW]>>16)&0x3FF;
			methodRIMTI = (input[OPERAND_A_LOW])&0xFFFF;
			output[RESULT_LOW] = methodArgs;
			setOutputValid(RESULT_LOW);
			break;
		case LOAD_ARG_RMTI:
					output[RESULT_LOW] = input[OPERAND_A_LOW];
					methodRMTI = output[RESULT_LOW]&0x3FF;
					methodArgs = (output[RESULT_LOW]>>10)&0x3F;
					output[RESULT_LOW] = methodArgs;
					setOutputValid(RESULT_LOW);
			break;
		case LDC:
			switch(tokenState){
			case 0: 
				constantPool.requestData(input[OPERAND_A_LOW]);
				tokenState = 1;
				return false;
			case 1:
				if(constantPool.requestData(input[OPERAND_A_LOW])){
					output[RESULT_LOW] = constantPool.getData().get(0);
					setOutputValid(RESULT_LOW);
					tokenState = 0;
				} else {
					return false;
				}
			}
			break;
		case LDC2:
			switch(tokenState){
			case 0: 
				constantPool.requestData(input[OPERAND_A_LOW]);
				tokenState = 1;
				return false;
			case 1:
				if(constantPool.requestData(input[OPERAND_A_LOW])){
					output[RESULT_HIGH] = constantPool.getData().get(0);
					tokenState = 2;
				}
				return false;
			case 2:
				constantPool.requestData(input[OPERAND_A_LOW]+1);
				tokenState = 3;
				return false;
			case 3:
				if(constantPool.requestData(input[OPERAND_A_LOW]+1)){
					output[RESULT_LOW] = constantPool.getData().get(0);
					setOutputValid(RESULT_HIGH);
					setOutputValid(RESULT_LOW);
					tokenState = 0;
				} else {
					return false;
				}
			}
			break;
		case INVOKE_STATIC:
			switch(tokenState){
			case 0:
					methodAMTI = input[OPERAND_A_LOW];
					methodTable.requestData(methodAMTI);
					tokenState = 1;
				return false;
			case 1:
				if(methodTable.requestData(methodAMTI)){
					MethodTableEntry method = methodTable.getData();
					methodBaseAddress = method.get(MethodTableEntry.CODE_REF);
					methodArgs = method.get(MethodTableEntry.NUMBER_ARGS);
					methodMaxLocals = method.get(MethodTableEntry.MAX_LOCALS);
					if( nativeMethods.containsKey(methodNames[methodAMTI])){
						// NATIVE METHOD
						if(frameStack.opcode != FrameStackOpcodes.INVOKE || heap.currentState != State.IDLE){
							return false;
						}
						int result = executeNativeMethod(methodNames[methodAMTI]);
						output[RESULT_LOW] = result;
						output[RESULT_HIGH] = (methodMaxLocals << 16) | (methodArgs)&0xFF | 0x100;   // <-- telling the framestack that this is native
						if(nativeMethods.get(methodNames[methodAMTI])){
							output[RESULT_HIGH] |= 0x200;											 // <-- telling the framestack that this native has a return value
						}
						setOutputValid(RESULT_LOW);
						setOutputValid(RESULT_HIGH);
						newAddress = distributionStage.getAddressOfJump() + 3; // go on after invoke
						executedJump = true;
						tokenState = 0;
						if(methodTracer.active()){
							methodTracer.println(methodPrefix + " native Method "+ methodNames[methodAMTI]);
						}
					} else {  
						
						int backJump = (distributionStage.getAddressOfJump() + 3) - currentMethodBaseAddress;
						
						
//						System.out.println("curramit  " + currentAMTI + " " + ((distributionStage.getAddressOfJump() + 3)));
//						System.out.println("  new am " + methodAMTI);
						// Send Results
						output[RESULT_LOW] = (currentAMTI<< 16 )| (backJump&0xFFFF);
						output[RESULT_HIGH] = (methodMaxLocals << 16) | (methodArgs)&0xFF;
						setOutputValid(RESULT_LOW);
						setOutputValid(RESULT_HIGH);
						// Set new context
//						System.out.println("INVOKING " +currentAMTI + "->" + methodAMTI +  " pc : " + programCounter);
						newAddress = methodBaseAddress;
						currentAMTI = methodAMTI;
						currentMethodBaseAddress = methodBaseAddress;
						//
						executedJump = true;
						tokenState = 0;
						if(methodTracer.active()){
							methodTracer.println(methodPrefix+"\\ "+ methodNames[currentAMTI]);
							methodPrefix = methodPrefix + "|";
						}
					}
				} else {
					return false;
				}
			}
			break;
		case INVOKE:
			switch(tokenState){
			case 0:
				if((input[OPERAND_A_LOW]&0xFFFF) == 0xFFFF){
					throw new AmidarSimulatorException("Nullpointer Exception while invoking. RMTI: "+methodRMTI);
				}
				tokenState  = 1;
				return false;
			case 1:
				if(classController.requestClassInfo(input[OPERAND_A_LOW])){
					methodAMTI = methodRMTI + classController.getData().get(ClassTableEntry.METHOD_TABLE_REF);
					methodTable.requestData(methodAMTI);
					addInvokation(distributionStage.getCurrentAddress(), input[OPERAND_A_LOW], methodAMTI);
					tokenState = 2;
				}
				return false;
			case 2:
				if(methodTable.requestData(methodAMTI)){
					MethodTableEntry method = methodTable.getData();
					methodBaseAddress = method.get(MethodTableEntry.CODE_REF);
					methodArgs = method.get(MethodTableEntry.NUMBER_ARGS);
					methodMaxLocals = method.get(MethodTableEntry.MAX_LOCALS);
					if( nativeMethods.containsKey(methodNames[methodAMTI])){
						// NATIVE METHOD
						if(frameStack.opcode != FrameStackOpcodes.INVOKE || heap.currentState != State.IDLE){
							return false;
						}
						int result = executeNativeMethod(methodNames[methodAMTI]);
						output[RESULT_LOW] = result;
						output[RESULT_HIGH] = (methodMaxLocals << 16) | (methodArgs)&0xFF | 0x100;   // <-- telling the framestack that this is native
						if(nativeMethods.get(methodNames[methodAMTI])){
							output[RESULT_HIGH] |= 0x200;											 // <-- telling the framestack that this native has a return value
						}
						setOutputValid(RESULT_LOW);
						setOutputValid(RESULT_HIGH);
						newAddress = distributionStage.getAddressOfJump() + 3; // go on after invoke
						executedJump = true;
						tokenState = 0;
						if(methodTracer.active()){
							methodTracer.println(methodPrefix + " native Method "+ methodNames[methodAMTI]);
						}
					} else {  
						// NON NATIVE METHOD
						// Send Results
//						System.out.println("curramit  " + currentAMTI + " " + ((distributionStage.getAddressOfJump() + 3)));
//						System.out.println("  new am " + methodAMTI);
						int backJump = (distributionStage.getAddressOfJump() + 3) - currentMethodBaseAddress;
						output[RESULT_LOW] = (currentAMTI<< 16 )| (backJump&0xFFFF);
						output[RESULT_HIGH] = (methodMaxLocals << 16) | (methodArgs)&0xFF;
						setOutputValid(RESULT_LOW);
						setOutputValid(RESULT_HIGH);
						// Set new context
						newAddress = methodBaseAddress;
						currentAMTI = methodAMTI;
						currentMethodBaseAddress = methodBaseAddress;
						//
						executedJump = true;
						tokenState = 0;
						if(methodTracer.active()){
							methodTracer.println(methodPrefix+"\\ "+ methodNames[currentAMTI]);
							methodPrefix = methodPrefix + "|";
						}
					}
				} else {
					return false;
				}
			}
			break;
		case INVOKE_INTERFACE:
			switch(tokenState){
			case 0:
				classController.requestClassInfo(input[OPERAND_A_LOW]);
				tokenState  = 1;
				return false;
			case 1:
				if(classController.requestClassInfo(input[OPERAND_A_LOW])){
					methodAMTI = classController.getData().get(ClassTableEntry.METHOD_TABLE_REF);
					interfaceTableIndex = classController.getData().get(ClassTableEntry.INTERFACE_TABLE_REF) + methodIOLI;
					interfaceTable.requestData(interfaceTableIndex);
					tokenState = 2;
				}
				return false;
			case 2:
				if(interfaceTable.requestData(interfaceTableIndex)){
					methodAMTI += methodRIMTI + interfaceTable.getData().get(0);
					methodTable.requestData(methodAMTI);
					addInvokation(distributionStage.getCurrentAddress(), input[OPERAND_A_LOW], methodAMTI);
					tokenState = 4;
				}
				return false;
			case 4:
				if(methodTable.requestData(methodAMTI)){
//					System.out.println("CALLINTE");
					MethodTableEntry method = methodTable.getData();
					methodBaseAddress = method.get(MethodTableEntry.CODE_REF);
					methodArgs = method.get(MethodTableEntry.NUMBER_ARGS);
					methodMaxLocals = method.get(MethodTableEntry.MAX_LOCALS);
					// Send Results
					int backJump = (distributionStage.getAddressOfJump() +5) - currentMethodBaseAddress;
					output[RESULT_LOW] = (currentAMTI<< 16 )| (backJump)&0xFFFF;
					output[RESULT_HIGH] = (methodMaxLocals << 16) | (methodArgs)&0xFF;
					setOutputValid(RESULT_LOW);
					setOutputValid(RESULT_HIGH);
					// Set new context
					newAddress = methodBaseAddress;
					currentAMTI = methodAMTI;
					currentMethodBaseAddress = methodBaseAddress;
					//
					executedJump = true;
					tokenState = 0;
					if(methodTracer.active()){
						methodTracer.println(methodPrefix+"\\ "+ methodNames[currentAMTI]);
						methodPrefix = methodPrefix + "|";
					}
				} else {
					return false;
				}
			}
			break;
		case JSR:
			output[RESULT_LOW] = distributionStage.getAddressOfJump() + 3;
			setOutputValid(RESULT_LOW);
			newAddress = distributionStage.getAddressOfJump() + input[OPERAND_A_LOW];
			executedJump = true;
			break;
		case RET:
			newAddress = input[OPERAND_A_LOW] + currentMethodBaseAddress;
			executedJump = true;
			setResultAck(true);
			break;
		case RETURN:
			switch(tokenState){
			case 0: 
				newAddress = input[OPERAND_A_LOW]&0xFFFF;
				methodAMTI = (input[OPERAND_A_LOW]>>16);
//				System.out.println("RET: "  + newAddress + "   amti " + methodAMTI);
				methodTable.requestData(methodAMTI);
				tokenState = 1;
				return false;
			case 1:
				if(methodTable.requestData(methodAMTI)){
					currentMethodBaseAddress = methodTable.getData().get(MethodTableEntry.CODE_REF);
					newAddress += currentMethodBaseAddress;
					if(methodTracer.active()){
						methodPrefix = methodPrefix.substring(0, methodPrefix.length()-1);
						methodTracer.println(methodPrefix+ "/ "+ methodNames[currentAMTI] + " " + currentAMTI);
						
						methodTracer.println(methodPrefix+ " "+ methodNames[methodAMTI] + " " + methodAMTI);
					}
//					System.out.println("CURR AMTI " + currentAMTI);
					currentAMTI = methodAMTI;
//					System.out.println("NEW AMTI " + currentAMTI + " ");
					executedJump = true;
					tokenState = 0;
					if(handlingException){ //TODO EXCEPTION
						currentInstruction = ByteCode.ATHROW;
						newAddress = distributionStage.getAddressOfJump();// DUNNO
//						programCounter -=1; // RETURN VALEU
					}
				} else {
					return false;
				}
			}
			setResultAck(true);
			break;			
		case NEWARRAY_CTI:
			switch(tokenState){
			case 0:
				classController.requestClassInfo(input[OPERAND_A_LOW]+ arrayTypeOffset);
				tokenState = 1;
				return false;
			case 1:
				if(classController.requestClassInfo(input[OPERAND_A_LOW]+ arrayTypeOffset)){
					ClassTableEntry classTableEnty = classController.getData();
					if(classTableEnty.get(ClassTableEntry.CLASSSIZE) == 3 || classTableEnty.get(ClassTableEntry.CLASSSIZE) == 7){
						output[RESULT_LOW] = 1<<31;
					} else {
						output[RESULT_LOW] = 0;
					}
					output[RESULT_LOW] |= (input[OPERAND_A_LOW]+arrayTypeOffset);
					setOutputValid(RESULT_LOW);
					tokenState = 0;		
				}
			}
			break;
		case MULTINEWARRAY_CTI:
			switch(tokenState){
			case 0:
				classController.requestClassInfo(input[OPERAND_A_LOW]);
				tokenState = 1;
				return false;
			case 1:
				if(classController.requestClassInfo(input[OPERAND_A_LOW])){
					ClassTableEntry classTableEnty = classController.getData();
					if(classTableEnty.get(ClassTableEntry.CLASSSIZE) == 3 || classTableEnty.get(ClassTableEntry.CLASSSIZE) == 7){
						output[RESULT_LOW] = 1<<31;
					} else {
						output[RESULT_LOW] = 0;
					}
					output[RESULT_LOW] |= (input[OPERAND_A_LOW]+arrayTypeOffset);
					setOutputValid(RESULT_LOW);
					tokenState = 0;		
				}
			}
			break;
		case INSTANCEOF:
			switch (tokenState) {
			case 0:
				classController.instanceOf(input[OPERAND_A_LOW], input[OPERAND_B_LOW]);
				if(classController.ready()){
					tokenState = 2;
				} else {
					tokenState = 1;
				}
				return false;
			case 1:
				if(classController.ready()){
					tokenState = 2;
				} else {
					tokenState = 1;
				}
				return false;
			case 2:
				output[RESULT_LOW] = classController.isInstanceOf();
				if(input[OPERAND_A_LOW] == 0xFFFF){
					output[RESULT_LOW] = 0;
				}
				setOutputValid(RESULT_LOW);
				tokenState = 0;
			default:
				break;
			}
			break;
		case CHECKCAST:
			switch (tokenState) {
			case 0:
				classController.instanceOf(input[OPERAND_A_LOW], input[OPERAND_B_LOW]);
				if(classController.ready()){
					tokenState = 2;
				} else {
					tokenState = 1;
				}
				return false;
			case 1:
				if(classController.ready()){
					tokenState = 2;
				} else {
					tokenState = 1;
				}
				return false;
			case 2:
				if( classController.isInstanceOf() != 1){
					throw new AmidarSimulatorException("Cannot Cast!!");
				}
				setResultAck(true);
				tokenState = 0;
			default:
				break;
			}
			break;
		case THROW:
			switch (tokenState){
			case 0:
				handlingException = true;
				if(input[OPERAND_A_LOW] == 0xFFFF){
					//TODO
					throw new AmidarSimulatorException("NullPointer Exception while Throwing an Exception");
				}
				if(currentAMTI == 0){
//					throw new AmidarSimulatorException("KAKKE EY " + input[OPERAND_A_LOW] + " " + input[OPERAND_B_LOW]);
					throw new AmidarSimulatorException("An exception was thrown on AMIDAR processor. Activate trace 'methods' in config/trace.json to find out more");
				}
				methodTable.requestData(currentAMTI);
				tokenState = 1;
				exceptionTableIndexCounter = 0;
				return false;
			case 1:
				if(methodTable.requestData(currentAMTI)){
					exceptionTableIndex = methodTable.getData().get(MethodTableEntry.EXCEPTION_TABLE_REF);
					exceptionLength = methodTable.getData().get(MethodTableEntry.EXCEPTION_TABLE_LENGTH);
					exceptionTable.requestData(exceptionTableIndex);
					tokenState = 2;
				}
				return false;
			case 2:
				if(exceptionTable.requestData(exceptionTableIndex)){
					if(exceptionTableIndexCounter >= exceptionLength ){
						// NO Exception found - throw exception again at calling method
						currentInstruction = ByteCode.ARETURN;
						output[RESULT_LOW] = input[OPERAND_B_LOW];
						setOutputValid(RESULT_LOW);
						executedJump = true;
						tokenState  = 0;
						return true;
					}
					entry = exceptionTable.getData();
					start = entry.get(ExceptionTableEntry.START);
					stop = entry.get(ExceptionTableEntry.END);
					expectedCTI = entry.get(ExceptionTableEntry.CATCH_TYPE_CTI);
					if(distributionStage.getAddressOfJump()< start || distributionStage.getAddressOfJump() >= stop){
						exceptionTableIndex++;
						exceptionTableIndexCounter++;
						exceptionTable.requestData(exceptionTableIndex);
						return false;
					} else if(expectedCTI == input[OPERAND_A_LOW]){
						int addr = entry.get(ExceptionTableEntry.PC_HANDLER);
						newAddress = addr;
						executedJump = true;
						tokenState = 0;
						output[RESULT_LOW] = input[OPERAND_B_LOW];
						setOutputValid(RESULT_LOW);
						handlingException = false;
						return true;
					} else { // Check parent
						classController.instanceOf(input[OPERAND_A_LOW], expectedCTI);
						if(classController.ready()){
							tokenState = 4;
						} else {
							tokenState = 3;
						}
						return false;
					}
				}
			case 3:
				if(classController.ready()){
					tokenState = 4;
				} else {
					tokenState = 3;
				}
				return false;
			case 4:
				if(classController.isInstanceOf() == 1){
					int addr = entry.get(ExceptionTableEntry.PC_HANDLER);
					newAddress = addr;
					executedJump = true;
					tokenState = 0;
					output[RESULT_LOW] = input[OPERAND_B_LOW];
					setOutputValid(RESULT_LOW);
					handlingException = false;
					return true;
				} else {
					exceptionTableIndex++;
					exceptionTableIndexCounter++;
					exceptionTable.requestData(exceptionTableIndex);
					tokenState = 2;
				}
				return false;
				
			}
			break;
		case NOP_CONST:
			break;
		case THREADSWITCH:
			break;
		case FORCESCHEDULING:
			break;
		case LOOP_0_BYTECODE_1:
			switch(tokenState){
			case 0:
				if(fetchParameter()){
					tokenState = 1;
				}
				return false;
			case 1:
				distributionStage.getTokenMatrix().setLoopIterations(getParameterByte(1));
				setResultAck(true);
				tokenState = 0;
			}
			break;
		case LOOP_0_BYTECODE_2:
			distributionStage.getTokenMatrix().setLoopIterations(getParameterByte(2));
			setResultAck(true);
			break;
		case LOOP_0_BYTECODE_3:
			distributionStage.getTokenMatrix().setLoopIterations(getParameterByte(3));
			setResultAck(true);
			break;
		case LOOP_0_BYTECODE_4:
			distributionStage.getTokenMatrix().setLoopIterations(getParameterByte(4));
			setResultAck(true);
			break;
		case INIT_LIVE_IN_OUT:
//			System.out.println("STARTING KERNEL " + input[OPERAND_A_LOW] + " on CGRA");
			kernelProfiler.startKernel(input[OPERAND_A_LOW]);
			SynthesizedKernelDescriptor oldKernel = currentKernel;
			if(oldKernel != null){
				oldKernel.addFollowerKernel(input[OPERAND_A_LOW]);
			}
			currentKernel = kernelTable[input[OPERAND_A_LOW]];
			synthConstPointer = currentKernel.getSynthConstPointer();
			output[RESULT_LOW] = input[OPERAND_A_LOW];
			setOutputValid(RESULT_LOW);
			break;
		case LOAD_LIVE_IN_OUT:
			
			output[RESULT_LOW] = liveInOutMemory[synthConstPointer++];
//			System.out.println("lilo: " + output[RESULT_LOW]);
			setOutputValid(RESULT_LOW);
			break;
		default:
		}

		return true;
	}

	@Override
	public boolean validInputs(TokenMachineOpcodes op) {
		switch(op){
		case BRANCH_IF_LE:
		case BRANCH_IF_GT:
		case BRANCH_IF_GE:
		case BRANCH_IF_LT:
		case BRANCH_IF_NE:
		case BRANCH_IF_EQ:
		case INSTANCEOF:
		case CHECKCAST:
		case THROW:
			return inputValid[OPERAND_A_LOW] && inputValid[OPERAND_B_LOW];
		case BRANCH:
		case CLASSSIZE:
		case LOAD_ARG_IOLI_RIMTI:
		case LDC:
		case LDC2:
		case JSR:
		case RET:
		case INVOKE:
		case INVOKE_INTERFACE:
		case RETURN:
		case NEWARRAY_CTI:
		case INIT_LIVE_IN_OUT:
		case MULTINEWARRAY_CTI:
			return inputValid[OPERAND_A_LOW];
		default: return true;

		}
	}

	public boolean tick(){
			boolean isReady = (currentState == State.IDLE) && !tokenValid; 
			State nextState = currentState;
			if(currentState == State.SENDING){
				if(getResultAck()){
					nextState= State.IDLE;
//					for(int i = 0; i < inputValid.length; i++){
//						inputValid[i] = false;
//					}
					for(int i = 0; i < outputValid.length; i++){
						outputValid[i] = false;
					}
//					tokenAdapter.nextToken();
					setResultAck(false);
				}		
				if(!tokenValid){
					tokenAdapter.nextToken();
				}
			}
			
			
			if(currentState == State.IDLE){
//				System.out.println(this + " öööö " + tokenValid + " + " + opcode);
				if(tokenValid && validInputs(opcode)){
					nextState = State.BUSY;
					count = getDuration(opcode);
					if(executeTrace.active()){
						executeTrace.println(this.toString()+ " starting "+ opcode + " ("+tag+")"); //TODO
					}
				} else if(!tokenValid){
					tokenAdapter.nextToken();
				}
			} else if(currentState == State.BUSY){
				count--;
				if(count <= 0){
					if(executeOp(opcode)){
						if(executeTrace.active()){
							executeTrace.println(this.toString()+ " executed "+ opcode + " ("+tag+")"); //TODO
							executeTrace.println("\toutput low: "+ output[RESULT_LOW]);
						}
						if(getResultAck()){
							nextState = State.IDLE;
							
							
							setResultAck(false);
						}
						else
							nextState = State.SENDING;
						operandAck(); //bei FUs an verschiedenen Stellen (wann Daten annehmen und damit Einfluss auf Busbelegung)
					}
				}
			} else if(currentState == State.SENDING0){
				nextState = State.SENDING;
			}
			currentState = nextState;
//			return isReady;
		
		
		boolean ready = isReady;			// Normal token execution
		ready  &= tickPipeline();				// Decode FSM
		return ready;
	}

	
	/**
	 * The states of the Decoder FSM 
	 * @author jung
	 *
	 */
	private enum DecodeState{
		IDLE,
		TOKEN_TREE,
		TOKEN_MATRIX,
		DONE,		// Sending Token from Token matrix is done
		FINISHED 	// Application is finished
	}
	

	private boolean tickPipeline(){
		if(branchPredictor.flushNeeded()){
			fetchStage.setFlush(branchPredictor.getPredictedAddress());
//			decodeStage.setFlush(branchPredictor.getPredictedAddress());
		}
		
		if(executedBranch){
			executedBranch = false;
			distributionStage.clearJumpTrap();
			if(!branchPredictor.correctPrediction(tookBranch)){
				fetchStage.setFlush(branchPredictor.getOtherAddress());
				decodeStage.setFlush(branchPredictor.getOtherAddress());
			} else {
				decodeStage.clearPrefetchTrap();
				decodeStage.clearJumpTrap();
			}
		} else if(executedJump){
			if(newAddress == actualApplicationStart){
				startedActualApplication = true;
				profiler = new Profiler();
				profiler.setMethodNames(methodNames);
				distributionStage.setProfiler(profiler);
			}
			executedJump = false;
			fetchStage.setFlush(newAddress);
			decodeStage.setFlush(newAddress);
			distributionStage.clearJumpTrap();
//			decodeStage.clearJumpTrap();
		}
//		System.out.println("tickdist");
		distributionStage.tick();
//		System.out.println("tick DECODE");
		decodeStage.tick();
//		System.out.println("tick FETCH");
		fetchStage.tick();
		
		
		if(currentAMTI == 0 && distributionStage.getCurrentBytecode() == ByteCode.GOTO){
			return true;
		}

		
		return false;
	}
	
//	/**
//	 * Handles the Decoder FSM
//	 * @return true if the decoder finished
//	 */
//	private boolean tickDecoder(){
//		if(decodeState == DecodeState.IDLE){
//			instructionMemoryAddress = applicationBaseAddress + currentMethodBaseAddress + programCounter;
//			if(!handlingException){
//				if(instructionMemoryAddress == actualApplicationStart){
//					startedActualApplication = true;
//					profiler = new Profiler();
//					profiler.setMethodNames(methodNames);
//				}
//				if(instructionCache.requestData(instructionMemoryAddress)){
//					profiler.newByteCode(instructionMemoryAddress);
//					currentInstruction = instructionCache.getData();
//					decodeState = DecodeState.TOKEN_TREE;
//				}
//			} else {
//				decodeState = DecodeState.TOKEN_MATRIX;
//			}
//		}else if(decodeState == DecodeState.TOKEN_TREE){
//			if(currentInstruction == ByteCode.GOTO && currentAMTI == 0){
//				decodeState = DecodeState.FINISHED;
//				endOfCode = true;
//			} else if(currentInstruction == ByteCode.NOP || currentInstruction == (byte)0xC2 || currentInstruction == (byte)0xC3){ // Special case as no Token have to be sent
//				isJump = false;
//				bytecodeOffset = 0;
//				sendConstantCount = 0;
//				decodeState = DecodeState.DONE;
//			} else {
//				decodeState = DecodeState.TOKEN_MATRIX;
//			}
//		}else if(decodeState == DecodeState.TOKEN_MATRIX){
//			decodeByteCode(currentInstruction);
//			decodeState = DecodeState.DONE;
//		}else if(decodeState == DecodeState.DONE){
//			boolean allTokensAccepted = tokenMachine.tokenAccepted() && heap.tokenAccepted() && ialu.tokenAccepted() && falu.tokenAccepted() && frameStack.tokenAccepted();
//			if(allTokensAccepted){
//				if(tokenDecodingDone()&& sendConstantsDone()){
//					if(isJump){
//						if(executedJump){
//							decodeState = DecodeState.IDLE;
//							executedJump = false;
//							sentConstants = 0;
//						}
//					} else {
//						programCounter += bytecodeOffset + 1;
//						decodeState = DecodeState.IDLE;
//						sentConstants = 0;
//					}
//				}else if(!tokenDecodingDone()){
//					decodeState = DecodeState.TOKEN_MATRIX;
//				}
//			}
//		}
//		return endOfCode; 
//	}

	/**
	 * Denotes whether all bytecode parameters of the current bytecode have been read form the instruction cache.
	 * (Need to know whether the next bytecode can beloaded).
	 * @return true if all bytecode parameters have been procssed
	 */
	private boolean sendConstantsDone(){
		return sentConstants >= sendConstantCount;
	}
	
	
	private boolean fetchParameter(){
//		if(parameterFifoNrConstants.isEmpty()){
//			return false;
//		}
		if(sendConstantsDone()){
			sendConstantCount = parameterFifoNrConstants.pop();
			parameter = parameterFifoParameter.pop();
//			System.out.println("~~~~~~~~~~~~~~Poopping " + sendConstantCount);
			sentConstants = 0;
//			return false;
		} else {
//			System.out.println(" ~~~notppop " + sentConstants + " of " + sendConstantCount);
		}
		return true;
	}
	
	private int getParameterByte(int theByte){
		int asdf;
		switch(theByte){
		case 1: 
			 asdf = (parameter >> 24)&0xFF;
			return (parameter >> 24)&0xFF;
		case 2:
			 asdf = (parameter >> 16)&0xFF;
			return (parameter >> 16)&0xFF;
		case 3:
			 asdf = (parameter >> 8)&0xFF;
			return (parameter >> 8)&0xFF;
		case 4: 
			 asdf = (parameter >> 0)&0xFF;
			return (parameter >> 0)&0xFF;
		default : return 0;
		}
	}


	/**
	 * Decodes the current bytecode and send the correct token to the FUs
	 * The actual method is generated by ADLA
	 * @param code the current bytecode
	 */
//	public abstract void decodeByteCode(byte code);

	/**
	 * Denotes whether all token the current bytecode have been sent.
	 * If not the method decodeByteCode(byte code) has to be called again on the same code
	 * @return true if all token have been sent and the next bytecode can be loaded
	 */
//	public abstract boolean tokenDecodingDone();

	public Profiler getProfiler() {
		return profiler;
	}

	public KernelProfiler getKernelProfiler() {
		return kernelProfiler;
	}
	
	private void addInvokation(int address, int cti, int amti){
		invokationHistory.add(new Invokation(address, cti, amti));
		if(invokationHistory.size() >= invokationHistoryLength){
			invokationHistory.remove(0);
		}
	}
	
	/**
	 * Energy produced by TokenMachine
	 */
	public double getAdditionalEnergy() {
		double energy = profiler.getGlobalBytecodeCount() * bytecodeEnergy;
		
		//TODO add cache miss energy
		
		return energy;
	}

	
	/**
	 * PSEUDO PERIPHERAL ACCESS
	 * @return
	 */
	public MethodDescriptor[] getMethodTable() {
		SimpleTableCache<MethodTableEntry> method = (SimpleTableCache<MethodTableEntry>) methodTable;
		
		MethodTableEntry[] methods = method.getMemory();
		MethodDescriptor[] result = new MethodDescriptor[methods.length];
		
		for(int i = 0; i<methods.length; i++){
			MethodDescriptor  md =new MethodDescriptor();
			MethodTableEntry mte = methods[i];
			md.setFlags(mte.get(MethodTableEntry.FLAGS));
			md.setCodeLength(mte.get(MethodTableEntry.CODE_LENGTH));
			md.setCodeRef(mte.get(MethodTableEntry.CODE_REF));
			md.setMaxLocals(mte.get(MethodTableEntry.MAX_LOCALS));
			md.setMaxStack(mte.get(MethodTableEntry.MAX_STACK));
			md.setNumberOfArgs(mte.get(MethodTableEntry.NUMBER_ARGS));
			md.setExceptionTableLength(mte.get(MethodTableEntry.EXCEPTION_TABLE_LENGTH));
			md.setExceptionTableRef(mte.get(MethodTableEntry.EXCEPTION_TABLE_REF));
			md.setMethodName(methodNames[i]);
			result[i] = md;
		}

		return result;
	}

	/**
	 * PSEUDO PERIPHERAL ACCESS
	 * @return
	 */
	public SynthesizedKernelDescriptor[] getKernelTable() {
		return kernelTable;
	}

	/**
	 * PSEUDO PERIPHERAL ACCESS
	 * @return
	 */
	public byte[] getCode() {
		return ((SimpleInstructionCache)instructionCache).getMemory();
	}
	
	/**
	 * PSEUDO PERIPHERAL ACCESS
	 * @return
	 */
	public int[] getliveInOutMemory() {
		return liveInOutMemory;
	}
	
	/**
	 * PSEUDO PERIPHERAL ACCESS
	 */
	public ArrayList<Invokation> getInvocationHistory(){
		return invokationHistory;
	}
	
	/**
	 * PSEUDO PERIPHERAL ACCESS
	 */
	public ConstantPoolEntry[] getConstantPool(){
		return ((SimpleTableCache<ConstantPoolEntry>)constantPool).getMemory();
	}
	
	/**
	 * ONLY FOR DEBUGGING
	 * @param methodNames
	 */
	public void setMethodNames(String[] methodNames) {
		this.methodNames = methodNames;
		profiler.setMethodNames(methodNames);
	}
	
	/**
	 * ONLY FOR DEBUGGING
	 */
	public String[] getMethodNames(){
		return methodNames;
	}
	
	/**
	 * Maps Handle of Simulated FileInputStream to native FileInputStream
	 */
	LinkedHashMap<Integer,FileInputStream> in = new LinkedHashMap<Integer,FileInputStream>();
	LinkedHashMap<Integer,File> files = new LinkedHashMap<Integer,File>();
	
	private long nativeMethodLongBuffer;
	
	/**
	 * NATIVE METHODS
	 */
	private int executeNativeMethod(String method){
		if(method.equals("java/io/PrintStream.println()V")){
			systemOutTracer.println();
			return 0;
		} else if(method.equals("java/io/PrintStream.print(I)V")){
			systemOutTracer.print(frameStack.memory[frameStack.stackPointer-1]);
			return 0;
		} else if (method.equals("java/io/PrintStream.print(D)V")){
			long val = (((long)frameStack.memory[frameStack.stackPointer-1])<<32)|((long)frameStack.memory[frameStack.stackPointer-2] & 0x00000000FFFFFFFFL);
			systemOutTracer.print(Double.longBitsToDouble(val));
			return 0;
		} else if (method.equals("java/io/PrintStream.print(J)V")){
			long val = (((long)frameStack.memory[frameStack.stackPointer-1])<<32)|((long)frameStack.memory[frameStack.stackPointer-2] & 0x00000000FFFFFFFF);
			systemOutTracer.print(val);
			return 0;
		} else if (method.equals("java/io/PrintStream.print(F)V")){
			systemOutTracer.print(Float.intBitsToFloat(frameStack.memory[frameStack.stackPointer-1]));
			return 0;
		} else if (method.equals("java/io/PrintStream.print(Z)V")){
			systemOutTracer.print(frameStack.memory[frameStack.stackPointer-1] == 1);
			return 0;
		} else if (method.equals("java/io/PrintStream.print(B)V")){
			systemOutTracer.print(frameStack.memory[frameStack.stackPointer-1]);
			return 0;
		} else if (method.equals("java/io/PrintStream.print(S)V")){
			systemOutTracer.print(frameStack.memory[frameStack.stackPointer-1]);
			return 0;
		} else if (method.equals("java/io/PrintStream.print(C)V")){
			systemOutTracer.print((char)frameStack.memory[frameStack.stackPointer-1]);
			return 0;
		} else if (method.equals("java/io/PrintStream.print(Ljava/lang/String;)V")){
//			systemOutTracer.println(frameStack.memory[frameStack.stackPointer-1]);
			int handle = frameStack.memory[frameStack.stackPointer-1];
			heap.objectCache.requestData(handle, 2);
			int length = heap.objectCache.getData(handle, 2);
			heap.objectCache.requestData(handle, 3);
			int offset = heap.objectCache.getData(handle, 3);
			heap.objectCache.requestData(handle, 0);
			int charHandle = heap.objectCache.getData(handle, 0);

//			int length = heap.mem.getSizeHT(charHandle);
			char[] res = new char[length];
			for(int i = 0; i< length; i++){
				heap.objectCache.requestData(charHandle, i+offset);
				res[i] = (char)heap.objectCache.getData(charHandle, i+offset);
			}
			systemOutTracer.print(new String(res));
			
			return 0;
		} else if(method.equals("java/io/PrintStream.print([C)V")){
//			Memory mem = heap.mem;
			
			int handle = frameStack.memory[frameStack.stackPointer-1];
			heap.htCache.requestData(handle);
			int addr = heap.htCache.getAddr();
			int length = heap.htCache.getSize();
			char[] res = new char[length];
			for(int i = 0; i< length; i++){
				heap.objectCache.requestData(handle, i);
				res[i] = (char)heap.objectCache.getData(handle, i);
			}
			systemOutTracer.print(new String(res));
			
		} else if(method.equals("java/io/PrintStream.flush()V")){
			systemOutTracer.flush();
		} else if (method.equals("java/io/FileInputStream.read()I")){
			
			int handle = frameStack.memory[frameStack.stackPointer-1];
			
//			System.err.println(new String(res));
			
			int ret = -1;
			try {
				FileInputStream input = in.get(handle);
				if(input == null){
					
					heap.objectCache.requestData(handle, 0);
					int fileStringHandle = heap.objectCache.getData(handle, 0);
					heap.objectCache.requestData(fileStringHandle, 0);
					int charHandle = heap.objectCache.getData(fileStringHandle, 0);
					heap.objectCache.requestData(fileStringHandle, 2);
					int length = heap.objectCache.getData(fileStringHandle, 2);
					heap.objectCache.requestData(fileStringHandle, 3);
					int offset = heap.objectCache.getData(fileStringHandle, 3);
					char[] res = new char[length];
					for(int i = 0; i< length; i++){
						heap.objectCache.requestData(charHandle, i+offset);
						res[i] = (char)heap.objectCache.getData(charHandle, i+offset);
					}
					
					input = new FileInputStream(new String(res));
					in.put(handle, input);
				}
				ret = input.read();
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return ret;
		} else if(method.equals("java/io/FileInputStream.read([BII)I")){
			int len = frameStack.memory[frameStack.stackPointer-1];
			int offset =frameStack.memory[frameStack.stackPointer-2];
			int resultHandle = frameStack.memory[frameStack.stackPointer-3];
			int handle = frameStack.memory[frameStack.stackPointer-4];
			
			if(heapTracer.active()) {
				heap.getOHTrace().appendTrace(ObjectHeap.INVALIDATE, 0, resultHandle, offset, len, 0, 0, 0);
			}
			
			
			int ret = -1;
			try {
				FileInputStream input = in.get(handle);
				if(input == null){
					heap.objectCache.requestData(handle, 0);
					int fileStringHandle = heap.objectCache.getData(handle, 0);
					heap.objectCache.requestData(fileStringHandle, 0);
					int charHandle = heap.objectCache.getData(fileStringHandle, 0);
					heap.objectCache.requestData(fileStringHandle, 2);
					int length = heap.objectCache.getData(fileStringHandle, 2);
					char[] res = new char[length];
					for(int i = 0; i< length; i++){
						heap.objectCache.requestData(charHandle, i);
						res[i] = (char)heap.objectCache.getData(charHandle, i);
					}
					input = new FileInputStream(new String(res));
					in.put(handle, input);
				}
				
				byte[] readValues = new byte[len+offset];
				ret = input.read(readValues, offset, len);
				

				for(int i = 0; i < ret; i++){
					heap.objectCache.writeData(resultHandle, offset+ i, readValues[i+offset]);
				}
				
				
				
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return ret;
		} else if(method.equals("java/io/File.lengthA()I")){
			int handle = frameStack.memory[frameStack.stackPointer-1];
			File input = files.get(handle);
			if(input == null){
				heap.objectCache.requestData(handle, 0);
				int fileStringHandle = heap.objectCache.getData(handle, 0);
				heap.objectCache.requestData(fileStringHandle, 0);
				int charHandle = heap.objectCache.getData(fileStringHandle, 0);
				heap.objectCache.requestData(fileStringHandle, 2);
				int length = heap.objectCache.getData(fileStringHandle, 2);
				char[] res = new char[length];
				for(int i = 0; i< length; i++){
					heap.objectCache.requestData(charHandle, i);
					res[i] = (char)heap.objectCache.getData(charHandle, i);
				}
				input = new File(new String(res));
				files.put(handle, input);
			}
			nativeMethodLongBuffer = input.length();
			return (int)(nativeMethodLongBuffer&0xFFFFFFFF);

		} else if(method.equals("java/io/File.lengthB()I")){
			return (int)((nativeMethodLongBuffer>>32)&0xFFFFFFFF);
		} else if(method.equals("java/lang/System.arraycopyN(Ljava/lang/Object;ILjava/lang/Object;II)V")){
			
			int length = frameStack.memory[frameStack.stackPointer-1];
			int destPos = frameStack.memory[frameStack.stackPointer-2];
			int destHandle = frameStack.memory[frameStack.stackPointer-3];
			int srcPos = frameStack.memory[frameStack.stackPointer-4];
			int srcHandle = frameStack.memory[frameStack.stackPointer-5];
			if(heapTracer.active()){
				heap.getOHTrace().appendTrace(ObjectHeap.INVALIDATE, 0, destHandle, destPos, length, 0, 0, 0);
			}
			
			heap.htCache.requestData(srcHandle);
			int flags = heap.htCache.getFlags();
			if((flags & 0x8000) != 0){ // 64 Bit array
				length = 2*length; 
				srcPos = 2*srcPos;
				destPos = 2*destPos;
			}
			for(int i = 0; i < length; i++){
				heap.objectCache.requestData(srcHandle, i+srcPos);
				int data =  heap.objectCache.getData(srcHandle, i+srcPos);
				heap.objectCache.writeData(destHandle, i+destPos, data);
			}
		} else if(method.equals("java/lang/Float.floatToIntBits(F)I") || method.equals("java/lang/Float.intBitsToFloat(I)F")){
				return frameStack.memory[frameStack.stackPointer-1];
		} else if(method.equals("java/lang/System.currentTimeMillisLow()I")){
//			System.err.println("TICKS: "+ticks);
			currentMillis = (long)(ticks * 0.66666666 * 0.000001);
			return (int)currentMillis;
		} else if(method.equals("java/lang/System.currentTimeMillisHigh()I")){
			return (int)(currentMillis>>32);
		} else if(method.equals("java/lang/System.nanoTimeLow()I")){
			System.err.println("TICKS: "+ticks);
			currentMillis = (long)(ticks * 0.66666666);
			System.err.println("TICKS: "+ticks + " nano: " + currentMillis);
			return (int)currentMillis;
		} else if(method.equals("java/lang/System.nanoTimeHigh()I")){
			System.err.println("TICKS: "+ticks + " nano: " + currentMillis);
			return (int)(currentMillis>>32);
		} else if(method.equals("graph500/Kernel2InputParserSparseParallel.readLine(Ljava/io/FileInputStream;[C)V")){
			int resultHandle = frameStack.memory[frameStack.stackPointer-1];
			int handle =frameStack.memory[frameStack.stackPointer-2];
			
			
			int ret = -1;
			try {
				FileInputStream input = in.get(handle);
				if(input == null){
					heap.objectCache.requestData(handle, 0);
					int fileStringHandle = heap.objectCache.getData(handle, 0);
					heap.objectCache.requestData(fileStringHandle, 0);
					int charHandle = heap.objectCache.getData(fileStringHandle, 0);
					heap.objectCache.requestData(fileStringHandle, 2);
					int length = heap.objectCache.getData(fileStringHandle, 2);
					char[] res = new char[length];
					for(int i = 0; i< length; i++){
						heap.objectCache.requestData(charHandle, i);
						res[i] = (char)heap.objectCache.getData(charHandle, i);
					}
					input = new FileInputStream(new String(res));
					in.put(handle, input);
				}
				
				char[] readValues = new char[2048];
				int cnt = 0;
				
				int val = input.read();
				
				while(val != '\n'){
					readValues[cnt++] = (char)val;
					val = input.read();
				}
				
				
//				ret = input.read(readValues, offset, len);
				

				for(int i = 0; i < cnt; i++){
					heap.objectCache.writeData(resultHandle, i, readValues[i]);
				}
				
				
				
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return ret;
		} else if(method.equals("graph500/Kernel2InputParserSparseParallel.readIJ(Ljava/io/FileInputStream;[I[II)V")){
			int N = frameStack.memory[frameStack.stackPointer-1];
			int JHandle = frameStack.memory[frameStack.stackPointer-2];
			int IHandle = frameStack.memory[frameStack.stackPointer-3];
			int handle =frameStack.memory[frameStack.stackPointer-4];
			
			
			int ret = -1;
			try {
				FileInputStream input = in.get(handle);
				if(input == null){
					heap.objectCache.requestData(handle, 0);
					int fileStringHandle = heap.objectCache.getData(handle, 0);
					heap.objectCache.requestData(fileStringHandle, 0);
					int charHandle = heap.objectCache.getData(fileStringHandle, 0);
					heap.objectCache.requestData(fileStringHandle, 2);
					int length = heap.objectCache.getData(fileStringHandle, 2);
					char[] res = new char[length];
					for(int i = 0; i< length; i++){
						heap.objectCache.requestData(charHandle, i);
						res[i] = (char)heap.objectCache.getData(charHandle, i);
					}
					input = new FileInputStream(new String(res));
					in.put(handle, input);
				}
				
				
				
				
				
				
				int [] ii = new int[N];
				int [] jj = new int[N];
				
				for(int i = 0; i < N; i++){
					char[] readValues = new char[2048];
					int cnt = 0;
					int val = input.read();
					
					while(val != '\n'){
						readValues[cnt++] = (char)val;
						val = input.read();
					}
				
					String str = new String(readValues,0,cnt);
					String[] vv = str.split(",");
					ii[i] = Integer.parseInt(vv[0]);
					jj[i] = Integer.parseInt(vv[1]);
				}

				for(int i = 0; i < N; i++){
					heap.objectCache.writeData(IHandle, i, ii[i]);
					heap.objectCache.writeData(JHandle, i, jj[i]);
				}
				
				
				
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return ret;
		} else if(method.equals("de/amidar/AmidarSystem.invalidateFlushAllCaches()V")){
			heap.invalidateFlushAllCaches();
		} else if(method.equals("de/amidar/cacheBench/CacheBenchParameters.getBenchmarkScale()I")){
			return getBenchmarkScale();
		}
			
		return 0;
	}

	
	
	
	public int getCurrentAddress() {
		return distributionStage.getCurrentAddress();
	}
	
	long currentMillis; 
	long nanoTime;
	
	long ticks;

	public void setTicks(long ticks) {
		this.ticks = ticks;
		
	}
	
	int benchmarkScale = 6;
	
	private int getBenchmarkScale(){
		return benchmarkScale;
	}
	
	public void setBenchmarkScale(int benchmarkScale){
		this.benchmarkScale = benchmarkScale;
	}
	
}
package functionalunit.cgra;

import com.sun.jndi.cosnaming.IiopUrl.Address;


/**
 * Classic registerfile that is used in a PE
 */
public class Registerfile {


	/**
	 * Data input of the register file
	 */
	int InputData;

	/**
	 * Address-port for writes
	 */
	int writeAddress;

	/**
	 * Port that determines a valid write.
	 */
	boolean writeEnable;

	/**
	 * Data outputs of the Registfile. There are n outputs available
	 */
	int OutputDirect,OutputMux,OutputCache;
	boolean OutputMuxDefined;
	boolean OutputDirectDefined;

	/**
	 * Inputs for Address for reads
	 */

	int addrDirect, addrMux, addrCache;

	/**
	 * Actual Register in the Registerfie.
	 */
	public int [] registers;
	public boolean [] registerusage;
	
	private int peId = -1;


	/**
	 * This method triggers the clocked processing of the register file as in writes. Currently
	 * the regfile processes simultaneous write and reads to the same register as
	 * using a bypass. This means the data corresponds to the data to be written.
	 * However, it might be beneficial to avoid this during scheduling.  
	 */
	protected void clocked(){
		if(writeEnable){
			registers[writeAddress]=InputData;
			registerusage[writeAddress] = true;
//			if(writeAddress ==7 || writeAddress == 6){
//				System.err.println("\tWriting "+peId+": "+InputData+ " to address "+ writeAddress);
//			}
//			if(InputData == -303253687){
//				System.out.println("\tWWWWWWriting -303253687 to " + writeAddress);
//			}
		} else {
//			if(writeAddress ==7 || writeAddress == 6){
//				System.out.println("\tNOTWriting "+peId+": "+InputData+ " to address "+ writeAddress);
//			}
//			if(InputData == 1228){
//				System.out.println("\tNOTWriting "+InputData+ " to address "+ writeAddress);
//			}
		}
//		System.out.print("\t\t");
//		for(int i = 0; i<10; i++){
//			System.out.print(registers[i]+ ",");
//		}System.out.println();
//		System.out.println("\t\t1:"+ +registers[1]);
//		System.out.println("\t\t44:"+ +registers[44]);
//		System.out.println("\t\t11:"+ +registers[11]);
	}

	/**
	 * Triggers the combinatorial emulation -> reads.
	 */
	protected void combinatorial(){
//		OutputDirect = registers[addrDirect];
//		OutputDirectDefined=true;// registerusage[addrDirect];
//		OutputMux = registers[addrMux];
//		OutputMuxDefined = true;// registerusage[addrMux];
//		OutputCache = registers[addrCache];	
//		
//		OutputDirectDefined= registerusage[addrDirect];
		OutputDirect = getValue(addrDirect);
		OutputMux = getValue(addrMux);
		OutputCache = getValue(addrCache);
//		OutputMuxDefined =  registerusage[addrMux];
	}
	
	
	private int getValue(int addr){
//		if(addr == 13 || addr == 5 || addr == 4 || addr == 3)
//		System.out.println("getting val: " + addr + " " + registers[addr]);
//		if(addr == 14/* && peId == 5*/){
//			System.out.println("REEEEEEEEEEEEEEEEEad: " + registers[14] + "    pe : " + peId);
//		}
//		
		
		if( addr < registers.length){
			return registers[addr];
		}
		
		int val = addr - registers.length;
		
		if(( val & (registers.length>>1)) != 0){
			val |= (-1 << (int)(Math.log(registers.length)/Math.log(2)));
		}
		
		
		
		
		return val;
		
	}
	
	
//	public static void main(String[] args){
//		
//		Registerfile rf = new Registerfile();
//		
//		rf.configure(32);
//		
//		System.out.println(rf.getValue(31));
//		System.out.println(rf.getValue(32));
//		System.out.println(rf.getValue(33));
//		System.out.println(rf.getValue(63));
//		System.out.println(rf.getValue(62));
//		
//		
//		
//	}
	
	
	

	/**
	 * Configures the memory
	 */
	protected int configure(int regs, int peId){
		registers = new int[regs];
		registerusage = new boolean[regs];
		this.peId = peId;
		return regs;
	}
	
	public void reset(){
		for(int i = 0; i <registers.length;i++){
			registers[i] = 0;
			registerusage[i] = false;
		}
	}



	/*
	 * Auto-generated getter and setter methods for all inputs and outputs
	 */
	protected void setWriteEnable(boolean atr){
		writeEnable = atr;
	}
	
	public boolean getWriteEnable(){
		return writeEnable;
	}

	protected void setWriteAddress(int adr){
		writeAddress = adr;
	}

	public int getInputData() {
		return InputData;
	}

	public void setInputData(int cacheData) {
		InputData = cacheData;
	}

	public int getOutputDirect() {
		return OutputDirect;
	}
	
	public boolean getOutputDirectDefined(){
		return OutputDirectDefined;
	}

	public void setOutputDirect(int outputDirect) {
		OutputDirect = outputDirect;
	}

	public int getOutputMux() {
		return OutputMux;
	}
	
	public boolean getOutputDefinedMux(){
		return OutputMuxDefined;
	}

	public void setOutputMux(int outputMux) {
		OutputMux = outputMux;
	}

	public int getOutputCache() {
		return OutputCache;
	}

	public void setOutputCache(int outputCache) {
		OutputCache = outputCache;
	}

	public int getAddrDirect() {
		return addrDirect;
	}

	public void setAddrDirect(int addrDirect) {
		this.addrDirect = addrDirect;
	}

	public int getAddrMux() {
		return addrMux;
	}

	public void setAddrMux(int addrMux) {
		this.addrMux = addrMux;
	}

	public int getAddrCache() {
		return addrCache;
	}

	public void setAddrCache(int addrCache) {
		this.addrCache = addrCache;
	}

	public int getWriteAddress() {
		return writeAddress;
	}

	public boolean isWriteEnable() {
		return writeEnable;
	}
	
	public int getSize(){
		return registers.length;
	}

}

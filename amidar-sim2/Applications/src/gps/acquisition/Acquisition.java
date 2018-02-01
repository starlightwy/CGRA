package gps.acquisition;


public class Acquisition {
	
	int nrOfSamples;
	final int fAbtast = 400000;
	final int fAbstand = 1000;  //fStep
	final int fMax = 5000;
	final int fMin = -5000;
	int m;
	final double grenz = 0.015;  //float?
	
	public Acquisition(int nrOfSamples){
		this.nrOfSamples = nrOfSamples;
		m = (int) (Math.ceil((fMax - fMin) / fAbstand) + 1);
	}
	
	public void enterSample(float real, float imag){
	}
	
	public void enterCode(float real, float imag){
	}
	
	public boolean startAcquisition(){
		
		return false;
	}
	
	public int getDopplerverschiebung(){
		return 0;
	}
	
	public int getCodeVerschiebung(){
		return 0;
	}
	
	

}

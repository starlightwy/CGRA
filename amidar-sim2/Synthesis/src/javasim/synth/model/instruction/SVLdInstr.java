package javasim.synth.model.instruction;

import java.util.LinkedList;
import java.util.Set;

import javasim.synth.HardGen;
import javasim.synth.SynthData;
import javasim.synth.model.I;
import javasim.synth.model.datum.AARead64Datum;
import javasim.synth.model.datum.AAReadDatum;
import javasim.synth.model.datum.AAWriteDatum;
import javasim.synth.model.datum.ConstDatum;
import javasim.synth.model.datum.Datum;
import javasim.synth.model.datum.Indexed;

/**
 * Static object reference load instruction.
 *
 * @author Michael Raitza
 * @version 21.09.2011
 */
public class SVLdInstr extends Instruction {

	public SVLdInstr(I instr, Integer pos) {
		super(instr, pos);
	}

	/**
	 * Evaluates this instruction and creates a new static object read datum.
	 * @param data holds the synthesis context as a SynthData object
	 * @see javasim.synth.model.I#eval
	 */
	public void eval(SynthData data) {
		Datum ref = new ConstDatum(1, I.SIPUSH.create(addr()));
		Datum index = new ConstDatum(i().getByteCodeParameter(data), I.SIPUSH.create(addr()));
		
		ref = data.dg().getConstant(ref);
		index = data.dg().getConstant(index);
		
		
		Datum res;
		if (i().wdata())
			res = new AARead64Datum(ref, index, index.value(), this);
		else
			res = new AAReadDatum(ref, index, index.value(), this);

		/* folding !! */
		LinkedList<Set<Datum>> hist = vstack().getHistory(res);
		if(hist != null && data.CSE()){
			for(Set<Datum> set: hist){
				if(set.size()==1){
					Datum d = set.iterator().next();
					if( d instanceof AAWriteDatum && ref.equals(d.reference())){
						if(((Indexed)d).index().equals(index) && data.lg().getLoop(d.creator().addr()).equals(data.lg().getLoop(addr()))){
							
							boolean fold = false;
							PHIInstr iiinst = res.creator().branchpoint();
							Boolean decision = res.creator().decision();
							do { // wirklich nur folden, wenn im gleichen branch - könnte man noch verbessern TODO (ist aber eher selten der fall)
								if(iiinst.equals(d.creator().branchpoint())){
									if( decision == d.creator().decision()){
										fold = true;
									}
									break;
								}else{
									decision = iiinst.decision();
									iiinst = iiinst.ifinstr().branchpoint();
								}
								
							} while (iiinst != null);
							
							if(fold){
								vstack().push(((AAWriteDatum)d).datum());
								super.eval(data);
								return;
							} else 
								break;
						}else
							break;
					}
					if(d instanceof Indexed){
						Indexed i = (Indexed)d;
						Datum oldIndex = i.index();
						if(oldIndex.equals(index) && ref.equals(d.reference())){ // prüfung auf reference weil aaload in gleicher history sein können aber versch arrays produzieren
							// hier muss mann tierisch aufpassen wo man annimt, ob das die gleichen referzen sind...
							boolean fold = false;
							PHIInstr iiinst = res.creator().branchpoint();
							Boolean decision = res.creator().decision();
							do { // wirklich nur folden, wenn im gleichen branch - könnte man noch verbessern TODO (ist aber eher selten der fall)
								if(iiinst.equals(d.creator().branchpoint()) ){
									if(decision == d.creator().decision()){
										fold = true;
									}
									break;
									
								}else{
									decision = iiinst.decision();
									iiinst = iiinst.ifinstr().branchpoint();
								}
								
							} while (iiinst != null);

							if(fold){
								vstack().push(d);	
								super.eval(data);
								return;
							}
						}
					}
				} else break;
			}

			
		}
		
		
		/// end folding
		
		data.dg().add_node(ref);
		data.dg().add_node(index);
		
		data.dg().add_op(res);
		if (branchpoint() != null)
			data.dg().add_sedge(branchpoint().ifdatum(), res);
		data.dg().add_edge(index, res, 2);
		data.dg().add_edge(ref, res, 1);
		vstack().push(res);
		
		
		vstack().object_add(data, res);
	
		super.eval(data);
	}
}

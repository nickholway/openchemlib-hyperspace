/**
 *
 * Idorsia Pharmaceuticals Ltd. 2020
 * Thomas Liphardt
 *
 */

package com.idorsia.research.chem.hyperspace.util;

import com.actelion.research.chem.*;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * NOTE!!! The returned bitsets indicate atom indeces in the CANONIZED molecule, and NOT
 *         necessarily indeces in the original molecule !!!
 *
 *
 * Enumerates all "connected heavy atom fragments", i.e. connected subgraphs of heavy atoms, of specific size.
 *
 * Uses the CONSENS algorithm (see supplement of Rarey "Connected Subgraph Fingerprints" publication
 * for explanation and proof of correctness).
 *
 */
public class FragmentGenerator {


    private final int mMinSize;
    private final int mMaxSize;

    // the molecule
    private final StereoMolecule M;

    // number of heavy atoms in M
    private final int SM;

    // forbidden
    private final BitSet[] forbidden;
    // neighbors
    private final BitSet[] N;


    final private List<BitSet> mFragments = new ArrayList<>();

    public List<BitSet> getFragments() {
        return mFragments;
    }

    public List<int[]> getFragmentsAsIntArrays() {
        List<int[]> fragments = new ArrayList<>(mFragments.size());
        for(int zi=0;zi<mFragments.size();zi++) {
            fragments.add(mFragments.get(zi).stream().toArray());
        }
        return fragments;
    }

    /**
     *
     * @return atom masks of fragments
     */
    public List<boolean[]> getFragmentsAsBooleanArrays() {
        List<boolean[]> fragments = new ArrayList<>(mFragments.size());
        for( int zi=0;zi<mFragments.size();zi++) {
            int[] frag_i = mFragments.get(zi).stream().toArray();
            boolean[] frag_b = new boolean[this.SM];
            for(int zj=0;zj<frag_i.length;zj++) {
                frag_b[frag_i[zj]] = true;
            }
            fragments.add(frag_b);
        }
        return fragments;
    }

    public List<StereoMolecule> getFragmentsAsMolecules() {
        List<boolean[]> frags_as_bool_arrays = this.getFragmentsAsBooleanArrays();
        List<StereoMolecule> fragments = new ArrayList<>(mFragments.size());

        for( int zi=0;zi<mFragments.size();zi++) {
            StereoMolecule mi = new StereoMolecule();
            this.M.copyMoleculeByAtoms( mi , frags_as_bool_arrays.get(zi) , true , null );
            mi.ensureHelperArrays(Molecule.cHelperCIP);
            fragments.add(mi);
        }
        return fragments;
    }

    /**
     *
     * @param m
     * @param min_size min number of heavy atoms in enumerated fragments
     * @param max_size max number of heavy atoms in enumerated fragments
     */
    public FragmentGenerator(StereoMolecule m, int min_size, int max_size) {

        if(false) {
            Canonizer c = new Canonizer(m);
            this.M = c.getCanMolecule(false);
            this.M.ensureHelperArrays(ExtendedMolecule.cHelperCIP);
        }
        else {
            this.M = new StereoMolecule(m);
            this.M.ensureHelperArrays(ExtendedMolecule.cHelperCIP);
        }
        this.SM = this.M.getAtoms();

        this.mMinSize = min_size;
        this.mMaxSize = max_size;

        // init forbidden and neighbors:
        this.forbidden = new BitSet[SM];
        for(int zi=0;zi<SM;zi++) {
            BitSet forbidden_i = new BitSet(this.SM);
            for(int zj=0;zj<zi;zj++) {
                forbidden_i.set(zj,true);
            }
            forbidden[zi] = forbidden_i;
//            forbidden_i.flip(0,SM);
//            forbidden[zi] = forbidden_i;
        }
        this.N = new BitSet[SM];
        for(int zi=0;zi<SM;zi++) {
            BitSet ni = new BitSet(this.SM);
            for(int zj=0;zj<M.getNonHydrogenNeighbourCount(zi);zj++) {
                ni.set(M.getConnAtom(zi,zj),true);
            }
            N[zi] = ni;
        }

        computeFragments();
    }

    public StereoMolecule getCanonizedInputMolecule() {
        return this.M;
    }

    public void computeFragments() {

        for(int zi=0;zi<this.SM;zi++) {
            BitSet frag = new BitSet();
            frag.set(zi,true);

            BitSet cand = new BitSet();
            cand = (BitSet) N[zi].clone();
            cand.andNot(forbidden[zi]);

            generate_recursive(frag, (BitSet) forbidden[zi].clone() , cand , 1);
        }
    }

    private void generate_recursive( BitSet frag, BitSet forbidden, BitSet cand , int frag_size) {

        //System.out.println("Frag: "+frag.toString());

//        int cardinality = frag.cardinality();
        int cardinality = frag_size;
        if( cardinality >= mMaxSize) {
            return;
        }

        for(int v=0;v<cand.size();v++) {
            if(cand.get(v)) {
                BitSet[] next = add_candidate(frag,forbidden,cand,v);
                if( (cardinality+1) >= mMinSize && (cardinality+1) <= mMaxSize) {
                    //System.out.println("add: "+next[0].toString());
                    mFragments.add(next[0]);
                }
                generate_recursive(next[0],next[1],next[2],frag_size+1);
            }
        }
    }

    /**
     * performs the add_candidate step
     *
     * @param frag
     * @param forbidden
     * @param cand
     * @param v
     * @return Array with { s' , f', c' }
     */
    private BitSet[] add_candidate(  BitSet frag, BitSet forbidden, BitSet cand , int v ) {
        BitSet frag_p = (BitSet) frag.clone();
        frag_p.set(v);

        BitSet forbidden_p = (BitSet) forbidden.clone();
        BitSet c2 = ((BitSet)cand.clone());
        c2.and((BitSet) this.forbidden[v].clone());
        forbidden_p.or( c2 );

        BitSet cand_p = (BitSet) cand.clone();
        cand_p.or(N[v]);
        cand_p.andNot(forbidden_p);
        cand_p.andNot(frag_p);

        return new BitSet[]{ frag_p, forbidden_p, cand_p };
    }



    public static void main(String args[]) {
        String smiles_A = "O1CCCCC1N1CCCCC1";
        String smiles_B = "O1C=C[C@H]([C@H]1O2)c3c2cc(OC)c4c3OC(=O)C5=C4CCC(=O)5"; // [not an idor compound]

        String idcode_C = "daD@@DjUZxHH@B";//"figq@@DL\\AIfUYyUywqZLzIV}ZjjdF@@Bh@@@";

        String smiles_D = "CCCC(C)N(CCC)CCC";


        SmilesParser sp = new SmilesParser();
        StereoMolecule mA = new StereoMolecule();
        StereoMolecule mB = new StereoMolecule();
        StereoMolecule mC = new StereoMolecule();
        StereoMolecule mD = new StereoMolecule();

        try {
            sp.parse(mA,smiles_A);
            sp.parse(mB,smiles_B);
            sp.parse(mD,smiles_D);
        } catch (Exception e) {
            e.printStackTrace();
        }



        IDCodeParser icp = new IDCodeParser();
        icp.parse(mC,idcode_C);


        FragmentGenerator fg_A = new FragmentGenerator(mB,10,10);
        fg_A.computeFragments();
        List<StereoMolecule> mols_a = fg_A.getFragmentsAsMolecules();
        System.out.println("Frag[idcode]");
        for(StereoMolecule mia : mols_a) {
            System.out.println(mia.getIDCode());
        }

        System.out.println("\n\n");

        //FragmentGenerator fg = new FragmentGenerator(mA,0,6);
        long ts_a = System.currentTimeMillis();
        FragmentGenerator fg = new FragmentGenerator(mB,10,10);
        long ts_b = System.currentTimeMillis();

        System.out.println("Num Fragments: "+fg.getFragments().size());
        System.out.println("Time: "+(ts_b-ts_a));

        System.out.println("Try enumerate all fragements for C");
        long ts_c = System.currentTimeMillis();
        FragmentGenerator fC = new FragmentGenerator(mC,1,40);
        fC.computeFragments();
        long ts_d = System.currentTimeMillis();
        System.out.println("Frags: "+fC.getFragments().size()+" Time: "+(ts_d-ts_c));

        System.out.println("Frags[idcode]");
        int cnt = 0;
        for(BitSet bci : fC.getFragments()) {
            StereoMolecule mi = new StereoMolecule();
            boolean bia[] = new boolean[mC.getAtoms()];
            for(int zi=0;zi<bia.length;zi++){if(bci.get(zi)){bia[zi]=true;}}
            mC.copyMoleculeByAtoms(mi,bia,true,null);
            if(cnt++<500) {
                System.out.println(mi.getIDCode());
            }
        }




        System.out.println("Try enumerate all fragements for D");
        long ts_e = System.currentTimeMillis();
        mD.ensureHelperArrays(Molecule.cHelperCIP);
        FragmentGenerator fD = new FragmentGenerator(mD,7,9);
        fD.computeFragments();
        long ts_f = System.currentTimeMillis();
        System.out.println("Frags: "+fD.getFragments().size()+" Time: "+(ts_f-ts_e));

        System.out.println("Frags[idcode]");
        cnt = 0;
        for(BitSet bci : fD.getFragments()) {
            StereoMolecule mi = new StereoMolecule();
            boolean bia[] = new boolean[mD.getAtoms()];
            for(int zi=0;zi<mD.getAtoms();zi++){if(bci.get(zi)){bia[zi]=true;}}
            //mD.copyMoleculeByAtoms(mi,bia,true,null); // THIS IS WRONG!!! (as mD is not yet canonized..)
            fD.getCanonizedInputMolecule().copyMoleculeByAtoms(mi,bia,true,null);
            if(cnt++<500) {
                System.out.println(mi.getIDCode());
            }
        }

        System.out.println("\n\n");
        System.out.println("Frags[idcode]");
        for( StereoMolecule mi : fD.getFragmentsAsMolecules() ) {
            System.out.println(mi.getIDCode());
        }

        System.out.println();
    }
}

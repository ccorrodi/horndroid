package z3;

import com.microsoft.z3.*;
import com.sun.org.apache.xpath.internal.operations.Bool;

import horndroid.options;

import org.apache.commons.lang3.RandomStringUtils;

import util.CMPair;

import javax.annotation.Nonnull;

import java.util.*;
import java.util.concurrent.*;

public class Z3Engine implements Z3Clauses {

    private Context mContext;
    //    private Fixedpoint mFixedPoint;
    private ArrayList<BoolExpr> mRules;
    private ArrayList<FuncDecl> mFuncs;

    private ArrayList<Z3Query> mQueries;
    private Z3Query mCurrentQuery;

    private int bvSize;
    private Z3Variable var;
    private Z3Function func;

    //legacy
    private int biggestRegisterNumber;
    public void updateBiggestRegister(final int i){
        if (i > this.biggestRegisterNumber) biggestRegisterNumber = i;
    }

    @Nonnull private final Set<CMPair> methodIsEntryPoint;
    @Nonnull private final Set<Integer> staticConstructor;

    public Z3Engine(options options){
        try {
            bvSize = options.bitvectorSize;
            mQueries = new ArrayList<>();

            //legacy
            this.methodIsEntryPoint = Collections.synchronizedSet(Collections.newSetFromMap(new ConcurrentHashMap<CMPair, Boolean>()));
            this.staticConstructor = Collections.synchronizedSet(Collections.newSetFromMap(new ConcurrentHashMap<Integer, Boolean>()));


            Global.setParameter("fixedpoint.engine", "pdr");
            //            Global.setParameter("fixedpoint.unbound_compressor", "false");
            Global.setParameter("pp.bv-literals", "false");

            HashMap<String, String> cfg = new HashMap<String, String>();
            mContext = new Context(cfg); //Context ctx = mContext;
            //            mFixedPoint = mContext.mkFixedpoint(); //Fixedpoint fp = mFixedPoint;
            mFuncs = new ArrayList<>();
            mRules = new ArrayList<>();

            // add vars
            var = new Z3Variable(mContext, bvSize);

            // add func
            func = new Z3Function(mContext, bvSize);
            this.declareRel(func.getH());
            this.declareRel(func.getHi());
            this.declareRel(func.getI());
            this.declareRel(func.getS());

            // add main
            BoolExpr b1 = hPred( var.getCn(), var.getCn(),
                    mContext.mkBV("parent".hashCode(), bvSize),
                    var.getF(), var.getLf(), var.getBf());
            BoolExpr b2 = hPred( var.getCn(), var.getCn(),
                    mContext.mkBV("result".hashCode(), bvSize),
                    var.getVal(), var.getLval(), var.getBval());
            BoolExpr b3 = hPred( var.getF(), var.getF(), var.getFpp(),
                    var.getVfp(), var.getLfp(), var.getBfp());
            BoolExpr b1b2b3 = mContext.mkAnd(b1, b2, b3);
            BoolExpr b4 = hPred( var.getF(), var.getF(),
                    mContext.mkBV("result".hashCode(), bvSize),
                    var.getVal(), var.getLval(), var.getBval());
            BoolExpr b1b2b3_b4 = mContext.mkImplies(b1b2b3, b4);

            this.addRule(b1b2b3_b4, null);
        } catch (Z3Exception e){
            e.printStackTrace();
            throw new RuntimeException("Z3Engine Failed");
        }
    }

    //legacy
    public void putEntryPoint(int c, int m){ methodIsEntryPoint.add(new CMPair (c, m));}
    public boolean isEntryPoint(int c, int m){
        return methodIsEntryPoint.contains(new CMPair(c, m));
    }
    public boolean hasStaticConstructor(int c){
        return staticConstructor.contains(c);
    }
    public void putStaticConstructor(int c){
        staticConstructor.add(c);
    }


    public Context getContext(){ return mContext; }

    public Z3Variable getVars(){ return var; }

    public Z3Function getFunc(){ return func; }

    public void addRule(BoolExpr rule, String symbol){
        try {
            //            mFixedPoint.addRule(rule, null);
            mRules.add(rule);
            //                    mContext.mkSymbol(RandomStringUtils.random(16,true,true)));
        } catch (Z3Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Z3Engine Failed: addRule");
        }
    }


    @Override
    public BoolExpr mkTrue() {
        try {
            return mContext.mkBool(true);
        } catch (Z3Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Z3Engine Failed: true");
        }
    }

    @Override
    public BoolExpr mkFalse() {
        try {
            return mContext.mkBool(false);
        } catch (Z3Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Z3Engine Failed: false");
        }
    }

    @Override
    public BoolExpr mkBool(boolean b) {
        try {
            return mContext.mkBool(b);
        } catch (Z3Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Z3Engine Failed: mkBool");
        }
    }

    @Override
    public BitVecExpr mkBitVector(String data, int len) {
        try {
            return mContext.mkBV(data, len);
        } catch (Z3Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Z3Engine Failed: mkBitVector String");
        }
    }

    @Override
    public BitVecExpr mkBitVector(int data, int len) {
        try {
            return mContext.mkBV(data, len);
        } catch (Z3Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Z3Engine Failed: mkBitVector int");
        }
    }

    @Override
    public BitVecExpr mkBitVector(long data, int len) {
        try {
            return mContext.mkBV(data, len);
        } catch (Z3Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Z3Engine Failed: mkBitVector long");
        }
    }

    @Override
    public IntExpr mkInt(String data) {
        try {
            return mContext.mkInt(data);
        } catch (Z3Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Z3Engine Failed: mkInt");
        }
    }

    @Override
    public IntExpr mkInt(int data) {
        try {
            return mContext.mkInt(data);
        } catch (Z3Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Z3Engine Failed: mkInt");
        }
    }

    @Override
    public BoolExpr and(BoolExpr... b) {
        try {
            return mContext.mkAnd(b);
        } catch (Z3Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Z3Engine Failed: and");
        }
    }

    @Override
    public BoolExpr or(BoolExpr... b) {
        try {
            return mContext.mkOr(b);
        } catch (Z3Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Z3Engine Failed: or");
        }
    }

    @Override
    public BoolExpr not(BoolExpr b) {
        try {
            return mContext.mkNot(b);
        } catch (Z3Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Z3Engine Failed: not");
        }
    }

    @Override
    public BoolExpr eq(BoolExpr b1, BoolExpr b2) {
        try {
            return mContext.mkEq(b1, b2);
        } catch (Z3Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Z3Engine Failed: eq");
        }
    }

    @Override
    public BoolExpr eq(BitVecExpr bv1, BitVecExpr bv2) {
        try {
            return mContext.mkEq(bv1, bv2);
        } catch (Z3Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Z3Engine Failed: eq");
        }
    }

    @Override
    public Expr ite(BoolExpr b, Expr e1, Expr e2) {
        try {
            return mContext.mkITE(b, e1, e2);
        } catch (Z3Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Z3Engine Failed: ite");
        }
    }

    @Override
    public BitVecExpr bvneg(BitVecExpr bv) {
        try {
            return mContext.mkBVNeg(bv);
        } catch (Z3Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Z3Engine Failed: bvneg");
        }
    }

    @Override
    public BitVecExpr bvnot(BitVecExpr bv) {
        try {
            return mContext.mkBVNot(bv);
        } catch (Z3Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Z3Engine Failed: bvnot");
        }
    }


    @Override
    public BitVecExpr bvadd(BitVecExpr bv1, BitVecExpr bv2) {
        try {
            return mContext.mkBVAdd(bv1, bv2);
        } catch (Z3Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Z3Engine Failed: bvadd");
        }
    }


    @Override
    public BitVecExpr bvmul(BitVecExpr bv1, BitVecExpr bv2) {
        try {
            return mContext.mkBVMul(bv1, bv2);
        } catch (Z3Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Z3Engine Failed: bvmul");
        }
    }


    @Override
    public BitVecExpr bvudiv(BitVecExpr bv1, BitVecExpr bv2) {
        try {
            return mContext.mkBVUDiv(bv1, bv2);
        } catch (Z3Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Z3Engine Failed: bvudiv");
        }
    }


    @Override
    public BitVecExpr bvurem(BitVecExpr bv1, BitVecExpr bv2) {
        try {
            return mContext.mkBVURem(bv1, bv2);
        } catch (Z3Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Z3Engine Failed: bvurem");
        }
    }


    @Override
    public BoolExpr bvugt(BitVecExpr bv1, BitVecExpr bv2) {
        try {
            return mContext.mkBVUGT(bv1, bv2);
        } catch (Z3Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Z3Engine Failed: bvugt");
        }
    }

    @Override
    public BoolExpr bvuge(BitVecExpr bv1, BitVecExpr bv2) {
        try {
            return mContext.mkBVUGE(bv1, bv2);
        } catch (Z3Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Z3Engine Failed: bvuge");
        }
    }

    @Override
    public BoolExpr bvule(BitVecExpr bv1, BitVecExpr bv2) {
        try {
            return mContext.mkBVULE(bv1, bv2);
        } catch (Z3Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Z3Engine Failed: bvule");
        }
    }

    @Override
    public BoolExpr bvult(BitVecExpr bv1, BitVecExpr bv2) {
        try {
            return mContext.mkBVULE(bv1, bv2);
        } catch (Z3Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Z3Engine Failed: bvult");
        }
    }

    @Override
    public BitVecExpr bvshl(BitVecExpr bv1, BitVecExpr bv2) {
        try {
            return mContext.mkBVSHL(bv1, bv2);
        } catch (Z3Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Z3Engine Failed: bvshl");
        }
    }

    @Override
    public BitVecExpr bvlshr(BitVecExpr bv1, BitVecExpr bv2) {
        try {
            return mContext.mkBVLSHR(bv1, bv2);
        } catch (Z3Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Z3Engine Failed: bvlshr");
        }
    }

    @Override
    public BitVecExpr bvashr(BitVecExpr bv1, BitVecExpr bv2) {
        try {
            return mContext.mkBVASHR(bv1, bv2);
        } catch (Z3Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Z3Engine Failed: bvashr");
        }
    }

    @Override
    public BitVecExpr bvsub(BitVecExpr bv1, BitVecExpr bv2) {
        try {
            return mContext.mkBVASHR(bv1, bv2);
        } catch (Z3Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Z3Engine Failed: bvsub");
        }
    }

    @Override
    public BitVecExpr bvxor(BitVecExpr bv1, BitVecExpr bv2) {
        try {
            return mContext.mkBVXOR(bv1, bv2);
        } catch (Z3Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Z3Engine Failed: bvxor");
        }
    }

    @Override
    public BitVecExpr bvor(BitVecExpr bv1, BitVecExpr bv2) {
        try {
            return mContext.mkBVOR(bv1, bv2);
        } catch (Z3Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Z3Engine Failed: bvor");
        }
    }

    @Override
    public BitVecExpr bvand(BitVecExpr bv1, BitVecExpr bv2) {
        try {
            return mContext.mkBVAND(bv1, bv2);
        } catch (Z3Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Z3Engine Failed: bvand");
        }
    }

    @Override
    public BoolExpr implies(BoolExpr b1, BoolExpr b2){
        try {
            return mContext.mkImplies(b1, b2);
        } catch (Z3Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Z3Engine Failed: implies");
        }
    }

    public void addQuery(Z3Query query){
        boolean askCompactQuery = true;

        boolean sameAsCurrentQuery =
                askCompactQuery
                && mCurrentQuery != null
                && mCurrentQuery.getClassName().equals(query.getClassName())
                && mCurrentQuery.getMethodName().equals(query.getMethodName())
                && mCurrentQuery.getPc().equals(query.getPc())
                && mCurrentQuery.getSinkName().equals(query.getSinkName());

        if( sameAsCurrentQuery ){
            // merge by or-ing queries
            mCurrentQuery.setQuery(
                    this.or(
                            mCurrentQuery.getQuery(),
                            query.getQuery()
                            )
                    );
        } else {
            // start new query
            if(mCurrentQuery != null) mQueries.add(mCurrentQuery);
            mCurrentQuery = query;
        }
    }

    public void executeAllQueries(){
        // ensure that the cached query is added
        if(mCurrentQuery != null) mQueries.add(mCurrentQuery);

        int threshold = 10;
        int timeout = 30; // 30 minutes

        //		ExecutorService executor = Executors.newFixedThreadPool(threshold);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        System.out.println("Number of queries: " + Integer.toString(mQueries.size()));

        for (int i = 0; i < mQueries.size(); i++) {

            final Z3Query q = mQueries.get(i);
            System.out.print((i + 1) + ": ");
            if (q.isVerbose())
                System.out.println(q.getDescription());

            final Fixedpoint temp = mContext.mkFixedpoint();
            for(BoolExpr rule : mRules){
                temp.addRule(rule, null);
            }
            for(FuncDecl func : mFuncs){
                temp.registerRelation(func);
                Symbol[] symbols = new Symbol[]{mContext.mkSymbol("interval_relation"),
                        mContext.mkSymbol("bound_relation")};
                temp.setPredicateRepresentation(func, symbols);
            }

            final Future<String> future = executor.submit(new Callable() {
                @Override
                public String call() throws Exception {

                    Status result = temp.query(q.getQuery());
                    System.out.println(result);

                    return result.toString();
                }
            });

            try {
                future.get(timeout, TimeUnit.MINUTES);
            } catch (TimeoutException e) {
                future.cancel(true);
            } catch (InterruptedException e) {
                future.cancel(true);
            } catch (ExecutionException e) {
                future.cancel(true);
            }
        }

        executor.shutdownNow();
    }


    public void declareRel(FuncDecl funcDecl){
        try {
            //            mFixedPoint.registerRelation(funcDecl);
            mFuncs.add(funcDecl);
        } catch (Z3Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Z3Engine Failed: declareRel");
        }
    }

    public void declareRel(String name, Sort[] domain, Sort range){
        try {
            FuncDecl f = mContext.mkFuncDecl(name, domain, range);
            this.declareRel(f);
        } catch (Z3Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Z3Engine Failed: declareRel");
        }
    }

    public void declareVar(Sort type){
        try {
            Expr var = mContext.mkBound(0, type);
        } catch (Z3Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Z3Engine Failed: declareVar");
        }
    }

    private FuncDecl rPredDef(String c, String m, int pc, int size) {
        try {
            BitVecSort bv64 = mContext.mkBitVecSort(bvSize);
            BoolSort bool = mContext.mkBoolSort();

            String funcName = "R_" + c + '_' + m + '_' + Integer.toString(pc);
            Sort[] domains = new Sort[3 * size];
            Arrays.fill(domains, 0, size, bv64);
            Arrays.fill(domains, size, 3 * size, bool);
            FuncDecl f = mContext.mkFuncDecl(funcName, domains, mContext.mkBoolSort());
            this.declareRel(f);
            //            Symbol[] symbols = new Symbol[]{mContext.mkSymbol("interval_relation"),
            //                                            mContext.mkSymbol("bound_relation")};
            //            mFixedPoint.setPredicateRepresentation(f, symbols);
            return f;
        } catch (Z3Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Z3Engine Failed: rPredDef");
        }
    }

    @Override
    public BoolExpr rPred(final String c, final String m, final int pc, final Map<Integer, BitVecExpr> rUp, final Map<Integer, BoolExpr> rUpL, final Map<Integer, BoolExpr> rUpB, final int numArg, final int numReg) {
        try {
            int size = numArg + numReg + 1; // include return register
            FuncDecl r = this.rPredDef(c, m, pc, size);

            Expr[] e = new Expr[3 * size];
            for(int i = 0, j = size, k = 2*size; i < size; i++, j++, k++){
                e[i] = rUp.get(i); if (e[i] == null) e[i] = var.getV(i);
                e[j] = rUpL.get(i); if (e[j] == null) e[j] = var.getL(i);
                e[k] = rUpB.get(i); if (e[k] == null) e[k] = var.getB(i);
            }

            return (BoolExpr) r.apply(e);
        } catch (Z3Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Z3Engine Failed: rPred");
        }
    }


    @Override
    public BoolExpr rInvokePred(final String c, final String m, final int pc, final Map<Integer, BitVecExpr> rUp, final Map<Integer, BoolExpr> rUpL, final Map<Integer, BoolExpr> rUpB, final int numArg, final int numReg, final int size) {
        try {
            int arraySize = numArg + numReg + 1;
            FuncDecl f = this.rPredDef(c, m, pc, arraySize);
            //        String name = "R" + '_' + c + '_' + m + '_' + Integer.toString(pc);

            Expr[] e = new Expr[3 * arraySize];
            for(int i = 0, j = arraySize, k = 2*arraySize; i < arraySize; i++, j++, k++){
                e[i] = rUp.get(i); if (e[i] == null) e[i] = this.mkBitVector(0, size);
                e[j] = rUpL.get(i); if (e[j] == null) e[j] = this.mkFalse();
                e[k] = rUpB.get(i); if (e[k] == null) e[k] = this.mkFalse();
            }

            return (BoolExpr) f.apply(e);
        } catch (Z3Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Z3Engine Failed: rInvokePred");
        }
    }

    private FuncDecl resPredDef(String c, String m, int size) {
        try {
            BitVecSort bv64 = mContext.mkBitVecSort(bvSize);
            BoolSort bool = mContext.mkBoolSort();

            String funcName = "RES_" + c + '_' + m;
            Sort[] domains = new Sort[3 * size];
            Arrays.fill(domains, 0, size, bv64);
            Arrays.fill(domains, size, 3 * size, bool);
            FuncDecl f = mContext.mkFuncDecl(funcName, domains, bool);

            this.declareRel(f);
            //            Symbol[] symbols = new Symbol[]{mContext.mkSymbol("interval_relation"),
            //                                            mContext.mkSymbol("bound_relation")};
            //            mFixedPoint.setPredicateRepresentation(f, symbols);
            return f;
        } catch (Z3Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Z3Engine Failed: resPredDef");
        }
    }

    @Override
    public BoolExpr resPred(final String c, final String m, final Map<Integer, BitVecExpr> rUp, final Map<Integer, BoolExpr> rUpL, final Map<Integer, BoolExpr> rUpB, final int numArg) {
        try {
            int size = numArg + 1; // include return register
            FuncDecl res = this.resPredDef(c, m, size);

            Expr[] e = new Expr[3 * size];
            for(int i = 0, j = size, k = 2*size; i < size; i++, j++, k++) {
                e[i] = rUp.get(i);
                if (e[i] == null) e[i] = var.getV(i);
                e[j] = rUpL.get(i);
                if (e[j] == null) e[j] = var.getL(i);
                e[k] = rUpB.get(i);
                if (e[k] == null) e[k] = var.getB(i);
            }

            return (BoolExpr) res.apply(e);
        } catch (Z3Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Z3Engine Failed: resPred");
        }
    }

    @Override
    public BoolExpr hPred(BitVecExpr cname, BitVecExpr inst, BitVecExpr element, BitVecExpr value, BoolExpr label, BoolExpr block) {
        try {
            return (BoolExpr) func.getH().apply(cname, inst, element, value, label, block);
        } catch (Z3Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Z3Engine Failed: hPred");
        }
    }

    @Override
    public BoolExpr hiPred(BitVecExpr cname, BitVecExpr inst, BitVecExpr value, BoolExpr label, BoolExpr block) {
        try {
            return (BoolExpr) func.getHi().apply(cname, inst, value, label, block);
        } catch (Z3Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Z3Engine Failed: hiPred");
        }
    }

    @Override
    public BoolExpr iPred(BitVecExpr cname, BitVecExpr inst, BitVecExpr value, BoolExpr label, BoolExpr block) {
        try {
            return (BoolExpr) func.getI().apply(cname, inst, value, label, block);
        } catch (Z3Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Z3Engine Failed: iPred");
        }
    }

    @Override
    public BoolExpr sPred(IntExpr v1, IntExpr v2, BitVecExpr v3, BoolExpr v4, BoolExpr v5) {
        try {
            return (BoolExpr) func.getS().apply(v1, v2, v3, v4, v5);
        } catch (Z3Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Z3Engine Failed: sPred");
        }
    }

}
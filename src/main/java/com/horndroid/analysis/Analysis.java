/*
 * MIT License
 *
 * Copyright (c) 2017 TU Wien
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.horndroid.analysis;

import com.horndroid.Dalvik.*;
import com.horndroid.Options;
import com.horndroid.payload.ArrayData;
import com.horndroid.payload.PackedSwitch;
import com.horndroid.payload.SparseSwitch;
import com.horndroid.strings.ConstString;
import com.horndroid.util.*;
import com.horndroid.z3.FSEngine;
import com.horndroid.z3.FSVariable;
import com.microsoft.z3.BitVecExpr;
import com.microsoft.z3.BoolExpr;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.iface.instruction.ReferenceInstruction;
import org.jf.dexlib2.iface.reference.FieldReference;
import org.jf.dexlib2.iface.reference.MethodReference;
import org.jf.dexlib2.iface.reference.Reference;
import org.jf.dexlib2.iface.value.EncodedValue;

import javax.annotation.Nonnull;
import java.util.AbstractMap.SimpleEntry;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;


public class Analysis {
    private static final Logger LOGGER = LogManager.getLogger(Analysis.class);
    final ExecutorService instructionExecutorService;
    final private Map<Integer, GeneralClass> apkClasses;
    final private Map<Integer, GeneralClass> classes;
    final private Instances apkInstances;
    final private Instances instances;
    final private Set<Integer> disabledActivities;
    final private Set<Integer> activities;
    final private Set<Integer> applications;
    final private Set<Integer> launcherActivities;
    final private Set<ConstString> constStrings;
    final private Set<Integer> callbackImplementations;
    final private Set<String> callbacks;
    final private Set<ArrayData> arrayDataPayload;
    final private Set<PackedSwitch> packedSwitchPayload;
    final private Set<SparseSwitch> sparseSwitchPayload;
    final private Set<Integer> overapprox;
    final private SourcesSinks sourcesSinks;
    final private Options options;
    final private FSEngine fsengine;
    final private FSVariable fsvar;
    final private Stubs stubs;
    @Nonnull
    private final Set<CMPair> methodIsEntryPoint;
    @Nonnull
    private final Set<Integer> staticConstructor;
    private Set<CMPair> refSources;
    private Set<CMPair> refSinks;
    private Map<Integer, Integer> allocationPointNumbers = new HashMap<Integer, Integer>();
    private Map<Integer, Integer> allocationPointNumbersReverse = new HashMap<Integer, Integer>();
    private Map<Integer, Integer> allocationPointSize = new HashMap<Integer, Integer>();
    private Map<Integer, Integer> allocationPointOffset = new HashMap<Integer, Integer>();
    private Map<Integer, String> allocationPointClass = new HashMap<Integer, String>();
    private Map<Integer, String> allocationPointClassDebug = new HashMap<Integer, String>();
    private Map<Integer, String> allocationPointMethod = new HashMap<Integer, String>();
    private Map<Integer, Integer> allocationPointPC = new HashMap<Integer, Integer>();

    private Interfaces interfaces = new Interfaces();

    private Integer localHeapNumberEntries;
    private Integer localHeapSize;
    private HashSet<StringPair> apkClassesMethods;

    private Set<Integer> methodHasSink;

    public Analysis(final FSEngine fsengine,
                    final SourcesSinks sourcesSinks, final Options options, final ExecutorService instructionExecutorService,
                    final Stubs stubs) {
        this.apkClasses = new ConcurrentHashMap<Integer, GeneralClass>();
        this.classes = new ConcurrentHashMap<Integer, GeneralClass>();

        this.apkInstances = new Instances();
        this.instances = new Instances();
        this.disabledActivities = new HashSet<Integer>();
        this.activities = new HashSet<Integer>();
        this.applications = new HashSet<Integer>();
        this.launcherActivities = new HashSet<Integer>();
        this.constStrings = new HashSet<ConstString>();
        this.callbackImplementations = new HashSet<Integer>();
        this.callbacks = new HashSet<String>();
        this.arrayDataPayload = new HashSet<ArrayData>();
        this.packedSwitchPayload = new HashSet<PackedSwitch>();
        this.sparseSwitchPayload = new HashSet<SparseSwitch>();
        this.overapprox = new HashSet<Integer>();
        this.instructionExecutorService = instructionExecutorService;
        this.sourcesSinks = sourcesSinks;
        this.fsengine = fsengine;
        this.fsvar = fsengine.getVars();
        this.options = options;

        this.stubs = stubs;

        this.methodHasSink = new HashSet<Integer>();


        this.refSources = new HashSet<CMPair>();
        this.refSinks = new HashSet<CMPair>();
        this.apkClassesMethods = new HashSet<StringPair>();

        this.overapprox.add("Landroid/content/ContentProvider;".hashCode());
        this.overapprox.add("Landroid/app/Service;".hashCode());
        this.overapprox.add("Landroid/content/BroadcastReceiver;".hashCode());
        this.overapprox.add("Landroid/app/Fragment;".hashCode());
        this.overapprox.add("Landroid/support/v4/app/FragmentActivity;".hashCode());
        this.overapprox.add("Landroid/support/v4/app/Fragment;".hashCode());
        this.overapprox.add("Landroid/app/ListFragment;".hashCode());
        this.overapprox.add("Landroid/support/v4/app/ListFragment;".hashCode());
        this.overapprox.add("Landroid/os/Handler;".hashCode());

        this.methodIsEntryPoint = new HashSet<CMPair>();
        this.staticConstructor = new HashSet<Integer>();
    }

    public boolean checkMethodHasSink(int cmHash) {
        if (methodHasSink != null) {
            return methodHasSink.contains(cmHash);
        } else return false;
    }

    public Set<Integer> getDisabledActivities() {
        return disabledActivities;
    }

    public Set<Integer> getActivities() {
        return activities;
    }

    public Set<Integer> getApplications() {
        return applications;
    }

    public Set<String> getCallbacks() {
        return callbacks;
    }

    public Set<Integer> getCallbackImplementations() {
        return callbackImplementations;
    }

    public Set<Integer> getLauncherActivities() {
        return launcherActivities;
    }

    public boolean isDebugging() {
        return options.debug;
    }

    public int getSize() {
        return options.bitvectorSize;
    }

    public boolean optionArrays() {
        return options.arrays;
    }

    public int optionFilterClasses() {return options.filterClasses;}

    public boolean optionOldUnknown() {
        return options.oldUnknown;
    }

    public boolean optionMerginPointers() {
        return options.pointersMerge;
    }

    public boolean optionSkipUnknown() {
        return options.nopUnknown;
    }

    public boolean optionVerbose() {
        return options.verboseResults;
    }

    public boolean optionFlowSensIfSink() {
        return options.sensIfHasSink;
    }

    public boolean optionNotFlowSens() {
        return options.nfsanalysis;
    }

    public Set<ArrayData> getArrayData() {
        return arrayDataPayload;
    }

    public Set<PackedSwitch> getPackedSwitch() {
        return packedSwitchPayload;
    }

    public Set<SparseSwitch> getSparseSwitch() {
        return sparseSwitchPayload;
    }

    public FSEngine getFSEngine() {
        if (!fsengine.isInitialized()) {
            throw new RuntimeException("Analysis.getFSEngine:FSEngine not initialized");
        }
        return fsengine;
    }

    public Set<Integer> getAllocationPoints() {
        return allocationPointOffset.keySet();
    }

    /*
     * Populate apkClasses and apkInstances with the classes and instances from the analysed apk
     * Also gather additional information: payloads data, switches information, static constructor and strings
     */
    public void collectDataFromApk(List<? extends ClassDef> classDefs,
                                   final Set<Integer> allowed) {
        allowed.addAll(launcherActivities); // adding launcher activities to the allowed
        DataExtraction de = new DataExtraction(apkClasses, apkInstances, arrayDataPayload, packedSwitchPayload, sparseSwitchPayload,
                staticConstructor, constStrings, launcherActivities, true, sourcesSinks, refSources, refSinks, methodHasSink, interfaces,
                allowed, optionFilterClasses(), options.filterClassesSound);
        de.collectData(classDefs);
    }

    /*
     * Should only be used in staticFieldLookUp(int, int)
     */
    private Integer staticFieldLookup(final GeneralClass ci, final int fi) {
        if (ci instanceof DalvikClass) {
            final DalvikClass dc = (DalvikClass) ci;
            for (final DalvikField f : dc.getExactFields()) {
                if (f.getName().hashCode() == fi)
                    return ci.getType().hashCode();
            }
            if (dc.getSuperClass() != null) {
                return staticFieldLookup(dc.getSuperClass(), fi);
            }
        }
        return null;
    }

    /*
     * Return the hashcode of the name of the super class of ci where fi is defined
     */
    public Integer staticFieldLookup(final int ci, final int fi) {
        if (classes.containsKey(ci)) {
            GeneralClass c = classes.get(ci);
            return staticFieldLookup(c, fi);
        }
        return null;
    }

    public DalvikMethod getExactMethod(final int ci, final int mi) {
        if (classes.containsKey(ci)) {
            GeneralClass c = classes.get(ci);
            if (c instanceof DalvikClass) {
                for (final DalvikMethod m : ((DalvikClass) c).getMethods()) {
                    if (m.getName().hashCode() == mi) {
                        return m;
                    }
                }
            }
        }
        return null;
    }

    public String getClassString(final int ci) {
        if (classes.containsKey(ci)) {
            GeneralClass c = classes.get(ci);
            if ((c instanceof DalvikClass) && c.getType() != null) {
                return c.getType();
            }
        }
        return Integer.toString(ci);
    }

    public GeneralClass getGeneralClass(final int ci) {
        if (classes.containsKey(ci)) {
            return classes.get(ci);
        }
        return null;
    }

    public String getMethodString(final int ci, final int mi) {
        if (classes.containsKey(ci)) {
            GeneralClass c = classes.get(ci);
            if ((c instanceof DalvikClass)) {
                for (final DalvikMethod m : ((DalvikClass) c).getMethods()) {
                    if (m.getName().hashCode() == mi && m.getName() != null) {
                        return m.getName();
                    }
                }
            }
        }
        return Integer.toString(mi);
    }

    /*
     * Return a bijective mapping (i --> field_i) between [[0;inputMap.size() - 1]] and the inputMap key-space
     */
    public Map<Integer, Integer> classFieldsMapping(final Map<Integer, Boolean> inputMap) {
        Map<Integer, Integer> result = Collections.synchronizedMap(new HashMap<Integer, Integer>(inputMap.size()));
        List<Integer> list = new ArrayList<Integer>(inputMap.keySet());
        Collections.sort(list);
        for (int i = 0; i < inputMap.size(); i++) {
            result.put(i, list.get(i));
        }
        return result;
    }

    /*
     * Generates the Horn Clauses for the ReachLH(v_1,v_2,h^*) predicate, 
     * which states that one local pointer v_2 is reachable from the local pointer v_1 in the local heap h^*
     * (The predicate only needs value and local label information from the local heap)
     */
    private void generateReachLHRules() {
        Map<Integer, BitVecExpr> lHValues = new HashMap<Integer, BitVecExpr>(localHeapSize);
        Map<Integer, BoolExpr> lHLocal = new HashMap<Integer, BoolExpr>(localHeapSize);

        // Base case
        fsengine.addRule(fsengine.reachLHPred(fsvar.getVal(), fsvar.getVal(), lHValues, lHLocal), null);

        // Induction case: there is one rule for each field of each object in the local heap
        BoolExpr h = fsengine.reachLHPred(fsvar.getVal(), fsvar.getVfp(), lHValues, lHLocal);
        for (int entry = 0; entry < localHeapNumberEntries; entry++) {
            int instanceNum = allocationPointNumbersReverse.get(entry);
            int offset = allocationPointOffset.get(instanceNum);
            int entrySize = allocationPointSize.get(instanceNum);
            for (int fieldNum = offset; fieldNum <= offset + entrySize; fieldNum++) {
                BoolExpr hh = fsengine.and(
                        h,
                        fsengine.eq(fsvar.getVfp(), fsengine.mkBitVector(instanceNum, getSize())),
                        fsengine.eq(fsvar.getLHV(fieldNum), fsvar.getRez()),
                        fsvar.getLHL(fieldNum)
                );
                BoolExpr b = fsengine.reachLHPred(fsvar.getVal(), fsvar.getRez(), lHValues, lHLocal);
                fsengine.addRule(fsengine.implies(hh, b), null);
            }
        }
    }

    /*
     * Generates the Horn Clauses used to compute connected components.
     * This is done in a slightly different way than in the formal model (as of 12 May 2016). 
     * I believe this formalization to be simpler for Z3.
     */
    private void generatesCFilter() {
        Map<Integer, BitVecExpr> lHValues = new HashMap<Integer, BitVecExpr>(localHeapSize);
        Map<Integer, BoolExpr> lHLocal = new HashMap<Integer, BoolExpr>(localHeapSize);
        Map<Integer, BoolExpr> lHFilter = new HashMap<Integer, BoolExpr>(localHeapSize);

        // Base case
        for (int i = 0; i < localHeapSize; i++) {
            lHFilter.put(i, fsengine.mkFalse());
        }
        fsengine.addRule(fsengine.cFilterPred(fsvar.getVal(), fsvar.getBf(), lHValues, lHLocal, lHFilter), null);
        lHFilter.clear();

        // Induction case: there is one rule for each object in the local heap
        BoolExpr h = fsengine.and(
                fsengine.reachLHPred(fsvar.getVal(), fsvar.getVfp(), lHValues, lHLocal),
                fsengine.cFilterPred(fsvar.getVal(), fsvar.getBf(), lHValues, lHLocal, lHFilter),
                fsvar.getBf()
        );
        for (int entry = 0; entry < localHeapNumberEntries; entry++) {
            int instanceNum = allocationPointNumbersReverse.get(entry);
            int offset = allocationPointOffset.get(instanceNum);
            int entrySize = allocationPointSize.get(instanceNum);

            BoolExpr hh = fsengine.and(
                    h,
                    fsengine.eq(fsvar.getVfp(), fsengine.mkBitVector(instanceNum, getSize()))
            );

            for (int fieldNum = offset; fieldNum <= offset + entrySize; fieldNum++) {
                lHFilter.put(fieldNum, fsengine.mkTrue());
            }

            BoolExpr b = fsengine.cFilterPred(fsvar.getVal(), fsvar.getBf(), lHValues, lHLocal, lHFilter);
            fsengine.addRule(fsengine.implies(hh, b), null);
            lHFilter.clear();
        }
    }
    
    /*
     * Generates the Horn Clauses for the LiftLH predicate.
     * We consider that an object in the local heap should be lifted iff its first field is label by true in the abstract filter
     */
    /*private void generatesLiftLH() {
        Map<Integer, BitVecExpr> lHValues = new HashMap<Integer,BitVecExpr>(localHeapSize);
    	Map<Integer, BoolExpr> lHHigh= new HashMap<Integer,BoolExpr>(localHeapSize);
    	Map<Integer, BoolExpr> lHLocal = new HashMap<Integer,BoolExpr>(localHeapSize);
    	Map<Integer, BoolExpr> lHGlobal = new HashMap<Integer,BoolExpr>(localHeapSize);
    	Map<Integer, BoolExpr> lHFilter = new HashMap<Integer,BoolExpr>(localHeapSize);


    	BoolExpr h = fsengine.liftLHPred(lHValues, lHHigh, lHLocal, lHGlobal, lHFilter);
    	for (int entry = 0; entry < localHeapNumberEntries; entry++){
    		int instanceNum = allocationPointNumbersReverse.get(entry);
    		int offset = allocationPointOffset.get(instanceNum);

			BoolExpr hh = fsengine.and(
					h,
					fsvar.getLHF(offset)
					);

            Map<Integer,Boolean> fields = getClassFields(getAllocationPointClass(instanceNum), instanceNum);
            int referenceIntIndex = getAllocationPointClass(instanceNum).hashCode();
            if (fields != null){
                int loopi = fsengine.getOffset(instanceNum);
                for (Map.Entry<Integer, Boolean> fieldN : fields.entrySet()){
                    BoolExpr b = fsengine.hPred(fsengine.mkBitVector(referenceIntIndex, getSize()),
                            fsengine.mkBitVector(instanceNum, getSize()),
                            fsengine.mkBitVector(fieldN.getKey(), getSize()),
                            fsvar.getLHV(loopi),
                            fsvar.getLHH(loopi),
                            fsengine.or(fsvar.getLHL(loopi),fsvar.getLHG(loopi)));
                    fsengine.addRule(fsengine.implies(hh, b), null);
                    loopi++;
                }   
            }
            else {
                BoolExpr b = fsengine.hPred(fsengine.mkBitVector(referenceIntIndex, getSize()),
                        fsengine.mkBitVector(instanceNum, getSize()),
                        fsvar.getF(), fsengine.mkBitVector(0, getSize()),
                        fsengine.mkFalse(), fsvar.getBf());
                fsengine.addRule(fsengine.implies(hh, b), null);
            }    		
    	}    	
    }*/


    private void initializeAllocationMapping() {
        Integer itNumber = 0;
        Integer offset = 0;

        Set<DalvikInstance> setOfInst = instances.getAllOnce();
        if (options.stubs) {
            setOfInst.addAll(stubs.getInstances().getAllOnce());
        }
        for (DalvikInstance i : setOfInst) {

            // check if instance is created in the method with a sink

            if ((!checkMethodHasSink((new CMPair(i.getC(), i.getM())).hashCode())) && options.sensIfHasSink) {
                continue;
            }

            // if instance is a result of an unknown method invocation - skip, cannot be local anyway

            if (!i.isNewInstance()){
                continue;
            }

            final int instanceNum = i.hashCode();

            final String referenceString = i.getType().getType();
            final Map<Integer, Boolean> fieldsMap = getClassFields(referenceString, instanceNum);
            TreeSet<Integer> fields = null;
            if (fieldsMap != null) {
                fields = new TreeSet<Integer>(fieldsMap.keySet());
            } else {
                fields = new TreeSet<Integer>();
            }
            allocationPointClass.put(instanceNum, referenceString);
            allocationPointClassDebug.put(instanceNum, this.getClassString(i.getC()));
            allocationPointMethod.put(instanceNum, this.getMethodString(i.getC(), i.getM()));
            allocationPointPC.put(instanceNum, i.getPC());
            allocationPointNumbers.put(instanceNum, itNumber);
            allocationPointNumbersReverse.put(itNumber, instanceNum);
            allocationPointSize.put(instanceNum, fields.size());
            allocationPointOffset.put(instanceNum, offset);
            offset += fields.size() + 1;
            itNumber += 1;
        }
        localHeapSize = offset;
        localHeapNumberEntries = itNumber;
    }

    public int getInstanceNumFromReverse(int i) {
        return allocationPointNumbersReverse.get(i);
    }

    public int getFieldOffset(int allocationPoint, int fieldIntReference) {
        TreeSet<Integer> fields = new TreeSet<Integer>(this.getClassFields(allocationPointClass.get(allocationPoint), allocationPoint).keySet());
        int i = 0;
        for (int field : fields) {
            if (field == fieldIntReference) {
                return i;
            }
            i++;
        }
        throw new RuntimeException("Analysis: getOffset: field does not exist");
    }

    public String getAllocationPointClass(int instanceNum) {
        return new String(allocationPointClass.get(instanceNum));
    }

    public String getAllocationPointClassDebug(int instanceNum) {
        return new String(allocationPointClassDebug.get(instanceNum));
    }

    public String getAllocationPointMethod(int instanceNum) {
        return new String(allocationPointMethod.get(instanceNum));
    }

    public int getAllocationPointPC(int instanceNum) {
        return allocationPointPC.get(instanceNum);
    }

    /*
     * Return a tree map of the format (hash code of the field name) -> (identifier whether the field has a primitive type)
     */
    public TreeMap<Integer, Boolean> getClassFields(final String className, final int instanceNum) {
        TreeMap<Integer, Boolean> result = new TreeMap<Integer, Boolean>();
        boolean found = false;
        if (classes.containsKey(className.hashCode())) {
            GeneralClass c = classes.get(className.hashCode());
            if (c instanceof DalvikClass && c != null) {
                final DalvikClass dc = (DalvikClass) c;
                found = true;
                for (DalvikField field : dc.getFields()) {
                    boolean prim = false;
                    final String fieldName = field.getName();
                    switch (fieldName) {
                        case "Z":
                        case "B":
                        case "S":
                        case "C":
                        case "I":
                        case "J":
                        case "F":
                        case "D":
                            prim = true;
                    }
                    result.put(fieldName.hashCode(), prim);
                }
            } else {
                LOGGER.info("getClassField " + className);
            }
        }

        if (!found) {
            String javaName = Utils.toStandardJavaClassName(className);
            try {
                Class<?> cc = Class.forName(javaName);
                java.lang.reflect.Field[] fields = cc.getFields();
                if (fields.length != 0)
                    for (java.lang.reflect.Field f : fields) {
                        result.put((className + "->" + f.getName() + ':' + Utils.toDalvikType(f.getType().toString())).hashCode(), f.getType().isPrimitive());
                    }
            } catch (Exception e) {
                return null;
            } catch (Error e) {
                return null;
            }
        }
        if (result.isEmpty()) return null;
        return result;
    }

    public Integer getInstNum(final int c, final int m, final int pc) {
        return DalvikInstance.hashCode(c, m, pc);
    }


    private void addToMain(final DalvikClass dc, final int methodIndex, final int numRegCall, final int regCount) {
        final int classIndex = dc.getType().hashCode();
        Map<Integer, BitVecExpr> regUpV = new HashMap<>();
        Map<Integer, BoolExpr> regUpH = new HashMap<>();
        Map<Integer, BoolExpr> regUpL = new HashMap<>();
        Map<Integer, BoolExpr> regUpG = new HashMap<>();
        Map<Integer, BitVecExpr> regUpLHV = new HashMap<>();
        Map<Integer, BoolExpr> regUpLHH = new HashMap<>();
        Map<Integer, BoolExpr> regUpLHL = new HashMap<>();
        Map<Integer, BoolExpr> regUpLHG = new HashMap<>();
        Map<Integer, BoolExpr> regUpLHF = new HashMap<>();
        if (!options.nfsanalysis) {
            // Register that are not arguments are initialized with 0
            for (int i = 0; i < numRegCall - regCount; i++) {
                regUpV.put(i, fsengine.mkBitVector(0, getSize()));
                regUpH.put(i, fsengine.mkFalse());
                regUpL.put(i, fsengine.mkFalse());
                regUpG.put(i, fsengine.mkFalse());
            }
            for (int i = 0; i <= numRegCall + regCount; i++) {
                regUpH.put(i, fsengine.mkFalse());
                regUpL.put(i, fsengine.mkFalse());
            }
            for (int i = 0; i < this.getLocalHeapSize(); i++) {
                regUpLHV.put(i, fsengine.mkBitVector(0, this.getSize()));
                regUpLHH.put(i, fsengine.mkFalse());
                regUpLHL.put(i, fsengine.mkFalse());
                regUpLHG.put(i, fsengine.mkFalse());
                regUpLHF.put(i, fsengine.mkFalse());
            }
            BoolExpr h = fsengine.rPred(Integer.toString(classIndex), Integer.toString(methodIndex), 0, regUpV, regUpH, regUpL, regUpG, regUpLHV, regUpLHH, regUpLHL, regUpLHG, regUpLHF, regCount, numRegCall);
            fsengine.addRule(h, dc.toString() + methodIndex + "zz");
        } else {
            // Register that are not arguments are initialized with 0
            for (int i = 0; i < numRegCall - regCount; i++) {
                regUpV.put(i, fsengine.mkBitVector(0, getSize()));
                regUpH.put(i, fsengine.mkFalse());
                regUpG.put(i, fsengine.mkFalse());
            }
            for (int i = 0; i <= numRegCall + regCount; i++) {
                regUpH.put(i, fsengine.mkFalse());
            }

            BoolExpr b1 = fsengine.rPred(Integer.toString(classIndex), Integer.toString(methodIndex), 0, regUpV, regUpH, regUpL, regUpG, regUpLHV, regUpLHH, regUpLHL, regUpLHG, regUpLHF, regCount, numRegCall);
            fsengine.addRule(b1, dc.toString() + methodIndex + "zz");
        }
    }

    private void addToMainHeap(final DalvikClass dc, final int methodIndex, final int numRegCall, final int regCount) {
        this.addToMain(dc, methodIndex, numRegCall, regCount);

        final int classIndex = dc.getType().hashCode();

        BoolExpr b2 = fsengine
                .hPred(fsengine.mkBitVector(classIndex, options.bitvectorSize),
                        // fsvar.getFpp(),
                        fsengine.mkBitVector(getInstNum(0, 0, 0),
                                options.bitvectorSize),
                        fsvar.getF(), fsvar
                                .getVal(), fsengine.mkFalse(), fsengine
                                .mkTrue());
        fsengine.addRule(b2, null);

        if (optionMerginPointers()) {
            // join
            b2 = fsengine.joinPred(fsengine.mkBitVector(getInstNum(0, 0, 0),
                    options.bitvectorSize), fsengine.mkFalse());
            fsengine.addRule(b2, null);
        }

    }


    public void processClass(final DalvikClass dc, final boolean isDisabledActivity, final boolean isCallbackImplementation,
                             final boolean isLauncherActivity, final boolean isApplication, final boolean isOverApprox) {
        for (final DalvikMethod m : dc.getMethods()) {
            if (!apkClassesMethods.contains(new StringPair(dc.getType(), m.getName()))) {
                continue;
            }
            boolean isCallback = false;
            for (final String callback : callbacks) {
                if (m.getName().contains(callback)) {
                    isCallback = true;
                }
            }

            final boolean isEntryPoint = testEntryPoint(dc, m.getName().hashCode());
            if (isCallbackImplementation) {
                addToMain(dc, m.getName().hashCode(), m.getNumReg(), m.getNumArg());
            }

            Map<Integer, BitVecExpr> regUpV = new HashMap<>();
            Map<Integer, BoolExpr> regUpH = new HashMap<>();
            Map<Integer, BoolExpr> regUpL = new HashMap<>();
            Map<Integer, BoolExpr> regUpG = new HashMap<>();
            Map<Integer, BitVecExpr> regUpLHV = new HashMap<>();
            Map<Integer, BoolExpr> regUpLHH = new HashMap<>();
            Map<Integer, BoolExpr> regUpLHL = new HashMap<>();
            Map<Integer, BoolExpr> regUpLHG = new HashMap<>();
            Map<Integer, BoolExpr> regUpLHF = new HashMap<>();

            if (isEntryPoint) {
                final int numRegCall = m.getNumReg();
                final int regCount = m.getNumArg();
                if (!options.nfsanalysis) {
                    // Register that are not arguments are initialized with 0
                    for (int i = 0; i < numRegCall - regCount; i++) {
                        regUpV.put(i, fsengine.mkBitVector(0, getSize()));
                        regUpH.put(i, fsengine.mkFalse());
                        regUpL.put(i, fsengine.mkFalse());
                        regUpG.put(i, fsengine.mkFalse());
                    }
                    for (int i = 0; i <= numRegCall + regCount; i++) {
                        regUpL.put(i, fsengine.mkFalse());
                        regUpH.put(i, fsengine.mkFalse());
                    }
                    for (int i = 0; i < this.getLocalHeapSize(); i++) {
                        regUpLHV.put(i, fsengine.mkBitVector(0, this.getSize()));
                        regUpLHH.put(i, fsengine.mkFalse());
                        regUpLHL.put(i, fsengine.mkFalse());
                        regUpLHG.put(i, fsengine.mkFalse());
                        regUpLHF.put(i, fsengine.mkFalse());
                    }
                    BoolExpr b1 = fsengine.iPred(fsengine.mkBitVector(dc.getType().hashCode(), options.bitvectorSize),
                            fsvar.getVfp(),
                            fsvar.getVal(), fsvar.getLf(), fsvar.getBf());

                    BoolExpr b2 = fsengine.rPred(Integer.toString(dc.getType().hashCode()), Integer.toString(m.getName().hashCode()), 0, regUpV, regUpH, regUpL, regUpG, regUpLHV, regUpLHH, regUpLHL, regUpLHG, regUpLHF, regCount, numRegCall);

                    fsengine.addRule(fsengine.implies(b1, b2), null);
                } else {
                    // Register that are not arguments are initialized with 0
                    for (int i = 0; i < numRegCall - regCount; i++) {
                        regUpV.put(i, fsengine.mkBitVector(0, getSize()));
                        regUpH.put(i, fsengine.mkFalse());
                        regUpG.put(i, fsengine.mkFalse());
                    }
                    for (int i = 0; i <= numRegCall + regCount; i++) {
                        regUpH.put(i, fsengine.mkFalse());
                    }

                    BoolExpr b1 = fsengine.iPred(fsengine.mkBitVector(dc.getType().hashCode(), options.bitvectorSize),
                            fsvar.getVfp(),
                            fsvar.getVal(), fsvar.getLf(), fsvar.getBf());

                    BoolExpr b2 = fsengine.rPred(Integer.toString(dc.getType().hashCode()), Integer.toString(m.getName().hashCode()), 0, regUpV, regUpH, regUpL, regUpG, regUpLHV, regUpLHH, regUpLHL, regUpLHG, regUpLHF, regCount, numRegCall);

                    BoolExpr b1tob2 = fsengine.implies(b1, b2);
                    fsengine.addRule(b1tob2, null);
                }
            }

            if (!isDisabledActivity && isEntryPoint && (isLauncherActivity || isApplication || isOverApprox)) {
                addToMainHeap(dc, m.getName().hashCode(), m.getNumReg(), m.getNumArg());
            }

            if (isCallback) {
                addToMain(dc, m.getName().hashCode(), m.getNumReg(), m.getNumArg());
            }

            int codeAddress = 0;
            for (final Instruction instruction : m.getInstructions()) {
                FSInstructionAnalysis ia = new FSInstructionAnalysis(this, instruction, dc, m, codeAddress);
                ia.CreateHornClauses(options, apkClassesMethods);
                codeAddress += instruction.getCodeUnits();
            }
        }
    }

    /*private String makeName(final GeneralClass c) {
        final String formatClassName = c.getType().replaceAll("\\.", "/").substring(1, c.getType().replaceAll("\\.", "/").length() - 1);
        final String[] parts = formatClassName.split("/");
        final String classN = parts[parts.length - 1];
        return classN;
    }*/

    /*
     * Return true if c'.getType().hashCode() is in overapprox where c' is either c or a super class of c
     */
    private boolean superIsInOverapprox(GeneralClass c) {
        if (overapprox.contains(c.getType().hashCode())) {
            return true;
        } else {
            if (c instanceof DalvikClass) {
                if (((DalvikClass) c).getSuperClass() != null) {
                    return superIsInOverapprox(((DalvikClass) c).getSuperClass());
                }
            }
        }
        return false;
    }


    /*
     * Check if c is in Overapprox:
     * more precisely if a child of c is in overapprox, or a superclass of c is in overapprox
     */
    private boolean testOverapprox(final GeneralClass c) {
        if (superIsInOverapprox(c)) {
            return true;
        } else {
            if (c instanceof DalvikClass) {
                final DalvikClass dc = (DalvikClass) c;
                for (final DalvikClass childClass : dc.getChildClasses()) {
                    if (overapprox.contains(childClass.getType().hashCode())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /*
     * Return true if makeName(c').hashCode() is in set where c' is either c or a super class of c
     */
    private boolean superIsInSet(Set<Integer> set, GeneralClass c) {
        if (set.contains(Utils.makeName(c).hashCode())) {
            return true;
        } else {
            if (c instanceof DalvikClass) {
                if (((DalvikClass) c).getSuperClass() != null) {
                    return superIsInSet(set, ((DalvikClass) c).getSuperClass());
                }
            }
        }
        return false;
    }

    /*
     * Check if c is in launcherActivities:
     * more precisely if a child of c is in launcherActivities, or a superclass of c is in launcherActivities
     */
    private boolean testLauncherActivity(final GeneralClass c) {
        if (c.getType() == null) {
            return false;
        }

        if (superIsInSet(launcherActivities, c)) {
            return true;
        } else {
            if (c instanceof DalvikClass) {
                final DalvikClass dc = (DalvikClass) c;
                for (final DalvikClass childClass : dc.getChildClasses()) {
                    if (launcherActivities.contains(Utils.makeName(childClass).hashCode())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean testDisabledActivity(final GeneralClass c) {
        return disabledActivities.contains(Utils.makeName(c).hashCode());
    }

    /*
     * Check if c is in applications:
     * more precisely if a child of c is in applications, or a superclass of c is in applications
     */
    private boolean testApplication(final GeneralClass c) {
        if (c.getType() == null) {
            return false;
        }

        if (superIsInSet(applications, c)) {
            return true;
        } else {
            if (c instanceof DalvikClass) {
                final DalvikClass dc = (DalvikClass) c;
                for (final DalvikClass childClass : dc.getChildClasses()) {
                    if (applications.contains(Utils.makeName(childClass).hashCode())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // generate labels for the R predicates
    public String mkLabel(DalvikClass c, DalvikMethod m, int pc) {
        return Integer.toString(c.getType().hashCode()) + "_" + Integer.toString(m.getName().hashCode()) + "_" + Integer.toString(pc);
    }

    private void addStaticFieldsValues() {
        for (GeneralClass c : classes.values()) {
            if (!(c instanceof DalvikClass))
                continue;
            for (DalvikField f : ((DalvikClass) c).getFields()) {
                if (f instanceof DalvikStaticField) {
                    final EncodedValue initialValue = ((DalvikStaticField) f)
                            .getDefaultValue();
                    BoolExpr rule = fsengine.sPred(fsengine.mkInt(Utils.Dec(c
                                    .getType().hashCode())), fsengine.mkInt(Utils.Dec(f
                                    .getName().hashCode())), FormatEncodedValue
                                    .toBitVec(fsengine, initialValue,
                                            options.bitvectorSize), fsengine.mkFalse(),
                            fsvar.getBf());
                    fsengine.addRule(rule, null);
                }
            }
        }
    }

    private void addClassFromApk(final GeneralClass cp, final LinkedList<SimpleEntry<GeneralClass, String>> pool,
                                 final Set<GeneralClass> addedInPool, final Set<CMPair> processCM) {
        if (!addedInPool.contains(cp) && cp != null) {
            addedInPool.add(cp);

            if (cp instanceof DalvikClass) {
                // Add all cp's methods to the pool and processCM set
                for (DalvikMethod m : ((DalvikClass) cp).getMethods()) {
                    pool.add(new SimpleEntry<GeneralClass, String>(cp, m.getName()));
                    processCM.add(new CMPair(cp.getType().hashCode(), m.getName().hashCode()));
                }
            }
        }
    }

    private void addToPool(LazyUnion lazyUnion, final LinkedList<SimpleEntry<GeneralClass, String>> pool,
                           final Set<CMPair> processCM, Map<DalvikClass, DalvikMethod> cmMap) {
        if (cmMap != null) {
            for (Entry<DalvikClass, DalvikMethod> entry : cmMap.entrySet()) {
                CMPair cmp = new CMPair(entry.getKey().getType().hashCode(), entry.getValue().getName().hashCode());
                if (!processCM.contains(cmp)) {
                    processCM.add(cmp);
                    pool.add(new SimpleEntry<GeneralClass, String>(entry.getKey(), entry.getValue().getName()));
                }
            }
        }
    }

    public Dispatch makeDispatch() {
        return new Dispatch(instances, classes, interfaces);
    }

    private void fetchNewInstance(final int cp, final String className, final int c, final int m, final int pc) {
        final GeneralClass gc = stubs.getClasses().get(cp);
        if (gc instanceof GeneralClass) {
            System.err.println("Unknown class, fields fetch failure: " + className);
        } else {
            if (gc instanceof DalvikClass) {
                final DalvikClass dc = (DalvikClass) gc;
                if (classes.containsKey(dc.getType().hashCode())) {
                    final GeneralClass gcNew = classes.get(dc.getType().hashCode());
                    if (gcNew instanceof DalvikClass) {
                        ((DalvikClass) gcNew).putFields(dc.getFields());
                    }
                    final DalvikInstance di = new DalvikInstance(c, m, pc, gcNew, true, true);
                    instances.add(di);
                } else {
                    final DalvikClass dcNew = new DalvikClass(dc.getType());
                    dcNew.putFields(dc.getFields());
                    dcNew.putSuperClass(dc.getSuperClass());
                    //dcNew.putInterfaces(dc.getInterfaces());
                    for (final DalvikClass child : dc.getChildClasses()) {
                        dcNew.putChildClass(child);
                    }
                    classes.put(dcNew.getType().hashCode(), dcNew);
                    final DalvikInstance di = new DalvikInstance(c, m, pc, dcNew, true, true);
                    instances.add(di);
                }
            }
        }
    }

    private void fetchInvoke(final DispatchResult dr) {
        for (final DalvikImplementation di : dr.getImplementations()) {
            final DalvikClass c = di.getDalvikClass();
            final DalvikMethod m = di.getMethod();
            boolean added = false;
            if (classes.containsKey(c.getType().hashCode())) {
                final GeneralClass gc = classes.get(c.getType().hashCode());
                if (gc instanceof DalvikClass) {
                    final DalvikClass dc = (DalvikClass) gc;
                    final Set<DalvikMethod> methods = Collections.newSetFromMap(new ConcurrentHashMap<DalvikMethod, Boolean>());
                    ;
                    for (final DalvikMethod dm : dc.getMethods()) {
                        methods.add(dm);
                    }
                    methods.add(m);
                    dc.putMethods(methods);
                    added = true;
                }
            }
            if (!added) {
                final DalvikClass dcNew = new DalvikClass(c.getType());
                dcNew.putSuperClass(c.getSuperClass());
                for (final DalvikClass child : c.getChildClasses()) {
                    dcNew.putChildClass(child);
                }
                //dcNew.putInterfaces(c.getInterfaces());
                final Set<DalvikMethod> methods = Collections.newSetFromMap(new ConcurrentHashMap<DalvikMethod, Boolean>());
                ;
                for (final DalvikMethod dm : c.getMethods()) {
                    methods.add(dm);
                }
                dcNew.putMethods(methods);
                classes.put(dcNew.getType().hashCode(), dcNew);
            }
        }
    }

    /*
     * Fetch the classes from standard java and android for all unknown classes
     */
    private Set<CMPair> fetchUnknownMethod() {
        DispatchResult dispatchResult = null;
        LinkedList<SimpleEntry<GeneralClass, String>> pool = new LinkedList<SimpleEntry<GeneralClass, String>>();
        Set<GeneralClass> addedInPool = new HashSet<GeneralClass>();
        Set<CMPair> processCM = new HashSet<CMPair>();

        LazyUnion lazyUnion = new LazyUnion(apkClasses, stubs.getClasses());
        Dispatch lazyDispatch = new Dispatch(instances, lazyUnion, interfaces);

        // We initialize the pool
        for (final GeneralClass c : classes.values()) {
            addClassFromApk(c, pool, addedInPool, processCM);
        }


        // We treat the pool until it is empty
        while (!pool.isEmpty()) {
            SimpleEntry<GeneralClass, String> entry = pool.poll();
            GeneralClass c = entry.getKey();
            String mString = entry.getValue();
            Utils.CallType callType = null;

            if (c instanceof DalvikClass) {
                final DalvikClass dc = (DalvikClass) c;
                DalvikMethod m = dc.getMethod(mString.hashCode());


                int codeAddress = 0;
                for (Instruction instruction : m.getInstructions()) {
                    if (instruction instanceof ReferenceInstruction) {
                        Reference reference = ((ReferenceInstruction) instruction).getReference();
                        if (reference instanceof FieldReference) {
                            int referenceClassIndex = ((FieldReference) reference).getDefiningClass().hashCode();
                            String referenceString = Utils.getShortReferenceString(reference);
                            fetchNewInstance(referenceClassIndex, referenceString, c.getType().hashCode(), m.getName().hashCode(), codeAddress);
                        } else {
                            if (reference instanceof MethodReference) {
                                String referenceString = Utils.getShortReferenceString(reference);
                                String referenceClass = ((MethodReference) reference).getDefiningClass();
                                int referenceClassIndex = ((MethodReference) reference).getDefiningClass().hashCode();
                                   
                               /*
                                * cloning for arrays is inherited from java.lang.Object
                                */
                                if (referenceString.equals("clone()Ljava/lang/Object;")
                                        && referenceClass.contains("[")) {
                                    referenceClass = "Ljava/lang/Object;";
                                    referenceClassIndex = referenceClass.hashCode();
                                }

                                Map<DalvikClass, DalvikMethod> cmMap = new HashMap<DalvikClass, DalvikMethod>();
                                switch (instruction.getOpcode()) {
                                    case INVOKE_SUPER:
                                    case INVOKE_SUPER_RANGE:
                                        callType = Utils.CallType.SUPER;
                                        break;
                                    case INVOKE_INTERFACE:
                                    case INVOKE_INTERFACE_RANGE:
                                        callType = Utils.CallType.INTERFACE;
                                        break;
                                    case INVOKE_VIRTUAL:
                                    case INVOKE_VIRTUAL_RANGE:
                                        callType = Utils.CallType.VIRTUAL;
                                        break;
                                    case INVOKE_DIRECT:
                                    case INVOKE_DIRECT_RANGE:
                                        callType = Utils.CallType.DIRECT;
                                        break;
                                    case INVOKE_STATIC:
                                    case INVOKE_STATIC_RANGE:
                                        callType = Utils.CallType.STATIC;
                                        break;
                                    default:
                                        throw new RuntimeException("MethodReference in a instruction which is fan invocation: " + instruction.getOpcode().toString());
                                }
                                dispatchResult = lazyDispatch.dispatch(referenceClassIndex, referenceString.hashCode(),
                                        ((MethodReference) reference).getDefiningClass(), referenceString, callType);
                                if (dispatchResult != null) {
                                    for (final DalvikImplementation di : dispatchResult.getImplementations()) {
                                        cmMap.put(di.getDalvikClass(), di.getMethod());
                                    }
                                    fetchInvoke(dispatchResult);
                                    addToPool(lazyUnion, pool, processCM, cmMap);
                                }
                            }
                        }
                    }
                    codeAddress += instruction.getCodeUnits();
                }
            }
        }
        fetchAdditionalInfo(processCM);
        return processCM;
    }


    /*
     * Get the additional information from the added classes, by querying stubs object and apkInstances
     * Should only be used once
     */
    private void fetchAdditionalInfo(Set<CMPair> processCM) {
        /*for (DalvikInstance instance: stubs.getInstances().getAll()){
            if (processCM.contains(new CMPair(instance.getC(), instance.getM()))){
                instances.add(instance);
            }
        }*/
        /*for (DalvikInstance instance: apkInstances.getAll()){
            if (processCM.contains(new CMPair(instance.getC(), instance.getM()))){
                instances.add(instance);
            }
        }*/

        for (ArrayData aData : stubs.getArrayDataPayload()) {
            if (processCM.contains(new CMPair(aData.getC(), aData.getM()))) {
                arrayDataPayload.add(aData);
            }
        }

        for (ConstString cString : stubs.getConstStrings()) {
            if (processCM.contains(new CMPair(cString.getC(), cString.getM()))) {
                constStrings.add(cString);
            }
        }

        for (PackedSwitch pSwitch : stubs.getPackedSwitchPayload()) {
            if (processCM.contains(new CMPair(pSwitch.getC(), pSwitch.getM()))) {
                packedSwitchPayload.add(pSwitch);
            }
        }

        for (SparseSwitch sSwitch : stubs.getSparseSwitchPayload()) {
            if (processCM.contains(new CMPair(sSwitch.getC(), sSwitch.getM()))) {
                sparseSwitchPayload.add(sSwitch);
            }
        }
        staticConstructor.addAll(stubs.getStaticConstructor());
        //instances.addSuperInstances();
    }

    public void createHornClauses() {
        LOGGER.info("Ready to start generating Horn Clauses:");

        /*
         * Initialize the set classes, which must be done *before* fetching
         * the unknown implementations from Java standard library.
         * We do not put in this set the classes or methods that contains "Landroid/support/v4(7)/"
         * At the same time we count the number of elements in APK only
         */
        int methodNumberAPK = 0;
        int instructionNumberAPK = 0;
        int classNumberAPK = 0;
        for (GeneralClass c : apkClasses.values()) {
            if (!(c.getType().startsWith("Landroid/support"))) {
                classes.put(c.getType().hashCode(), c);
                classNumberAPK++;
                if (c instanceof DalvikClass) {
                    for (DalvikMethod m : ((DalvikClass) c).getMethods()) {
                        methodNumberAPK++;
                        instructionNumberAPK += m.getInstructions().size();
                    }
                }
            }
        }
       /*
        * Add instances from the added classes
        */
        for (DalvikInstance di : apkInstances.getAllOnce()) {
            if (classes.containsKey(di.getC())) {
                instances.add(di);
            }
        }
        addEntryPointsInstances();
        LOGGER.info("Number of classes in APK : " + classNumberAPK);
        LOGGER.info("Number of methods in APK: " + methodNumberAPK);
        LOGGER.info("Number of instructions in APK: " + instructionNumberAPK);
        LOGGER.info("Number of instances in APK: " + instances.size());

        // Get the unknown classes from Java standard and Android libraries
        Set<CMPair> processCM = fetchUnknownMethod();

        //Counting the number of instructions and initializing apkClassMethods
        int instructionNumber = 0;
        for (CMPair cmp : processCM) {
            GeneralClass c = classes.get(cmp.getC());
            if ((c instanceof DalvikClass)) {
                DalvikMethod m = ((DalvikClass) c).getMethod(cmp.getM());
                if (m != null) { // will be null if method is defined in a super class
                    apkClassesMethods.add(new StringPair(c.getType(), m.getName()));
                    instructionNumber += m.getInstructions().size();
                }
            }
        }

        // Initialize allocationPointOffset,allocationPointNumbers and allocationPointSize
        if (!options.nfsanalysis) {
            initializeAllocationMapping();
        }


        if (options.nfsanalysis) {
            fsengine.initializeNFS();
        } else {
            // Correctly set the corresponding fields in the FSEngine
            fsengine.initialize(localHeapSize, allocationPointOffset, allocationPointSize);

            // Generates heap handling rules for the flow-sensitive analysis
            generateReachLHRules();
            generatesCFilter();
            //generatesLiftLH();
        }

        addStaticFieldsValues();

        printSourceSink();

        LOGGER.info("Number of classes : " + classes.size());
        LOGGER.info("Number of methods: " + processCM.size());
        LOGGER.info("Number of instructions: " + instructionNumber);
        LOGGER.info("Number of instances : " + instances.size());
        //System.out.print("Number of processed instructions : " + numberOfHornCLauseInstructions);


        for (final GeneralClass c : classes.values()) {
            if ((c instanceof DalvikClass)) {
                final DalvikClass dc = (DalvikClass) c;
                final boolean isDisabledActivity = testDisabledActivity(dc);
                final boolean isLauncherActivity = testLauncherActivity(dc);
                final boolean isApplication = testApplication(dc);
                final boolean isOverapprox = testOverapprox(dc);
                boolean isCallbackImplementation = false;
                final HashSet<Integer> ic = interfaces.getByClassType(dc.getType().hashCode());
                if (ic != null) {
                    for (final Integer interfaceC : ic) {
                        if (callbackImplementations.contains(interfaceC)) {
                            isCallbackImplementation = true;
                        }
                    }
                }
                final boolean isci = isCallbackImplementation;
                processClass(dc, isDisabledActivity, isci, isLauncherActivity, isApplication, isOverapprox);
            }
        }
    }


    private boolean testEntryPoint(final GeneralClass c, final int methodIndex) {
        if (this.isEntryPoint(c.getType().hashCode(), methodIndex)) {
            return true;
        }

        if (c instanceof DalvikClass) {
            final DalvikClass dc = (DalvikClass) c;
            final GeneralClass superClass = dc.getSuperClass();
            if (superClass == null) {
                return false;
            } else {
                return testEntryPoint(superClass, methodIndex);
            }
        }
        return false;

    }

    private void addEntryPointsInstances() {
        for (final GeneralClass c : classes.values()) {
            if (c instanceof DalvikClass) {
                final DalvikClass dc = (DalvikClass) c;
                for (final DalvikMethod method : dc.getMethods()) {
                    final int methodIndex = method.getName().hashCode();
                    if (!testDisabledActivity(dc) && testEntryPoint(dc, methodIndex)
                            && (testLauncherActivity(dc)
                            || testApplication(dc)
                            || testOverapprox(dc))) {
                        instances.add(new DalvikInstance(0, 0, 0, dc, true, false)); // entrypoint instances are always global, hence isNewIntsance is set to false
                        break;
                    }
                }
            }
        }
    }


    public int getLocalHeapSize() {
        return localHeapSize;
    }

    public int getLocalHeapNumberEntries() {
        return localHeapNumberEntries;
    }

    /*
     * Return true if c,m is a source, and if className, methodName is a method in the initial apk, and not
     * a method fetched from Java standard library or Android library
     */
    //TODO:
    public boolean isSource(String className, String methodName, final int c, final int m) {
        return (refSources.contains(new CMPair(c, m)));
    }

    //TODO: used only in processIntent in standard analysis, should probably be removed
    public boolean isSourceBis(final int c, final int m) {
        return refSources.contains(new CMPair(c, m));
    }

    /*
     * Return true if c,m is a sink, and if className, methodName is a method in the initial apk, and not
     * a method fetched from Java standard library or Android library
     */
    //TODO:
    public boolean isSink(String className, String methodName, final int c, final int m) {
        return (refSinks.contains(new CMPair(c, m)));
    }

    public void putEntryPoint(int c, int m) {
        methodIsEntryPoint.add(new CMPair(c, m));
    }

    public boolean isEntryPoint(int c, int m) {
        return methodIsEntryPoint.contains(new CMPair(c, m));
    }

    public boolean hasStaticConstructor(int c) {
        return staticConstructor.contains(c);
    }

    private void printSourceSink() {
        LOGGER.info("Number of sources: " + refSources.size());
        LOGGER.info("Number of sinks: " + refSinks.size());
    }

    public boolean isFlowSens() {
        return !options.nfsanalysis;
    }

    public int getDebugNumber() {
        return options.debugInt;
    }

    public int getAllocationPointNumbersReverse(int entry) {
        return allocationPointNumbersReverse.get(entry);
    }

}

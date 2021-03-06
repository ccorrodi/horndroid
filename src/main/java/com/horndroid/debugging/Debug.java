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

package com.horndroid.debugging;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;

import com.horndroid.Dalvik.DalvikMethod;
import com.horndroid.analysis.Analysis;
import com.horndroid.util.Utils;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.iface.instruction.ReferenceInstruction;
import org.jf.dexlib2.iface.reference.MethodReference;
import org.jf.dexlib2.iface.reference.Reference;


public class Debug {
    private Analysis analysis;
    private HashMap<String,HashMap<String,MethodeInfo>> debug = new HashMap<>();

    public Debug(Analysis analysis){
        this.analysis = analysis;
    }
    
    public final MethodeInfo get(String c,String m){
        if (debug.containsKey(c)){
          if(debug.get(c).containsKey(m)){
              return debug.get(c).get(m);
          }else{
              DalvikMethod dm = analysis.getExactMethod(c.hashCode(), m.hashCode());
              int numReg = dm.getNumReg() + 1;
              MethodeInfo mi = new MethodeInfo(analysis,c,m,numReg);
              debug.get(c).put(m, mi);
              return mi;
          }
        }else{
            debug.put(c, new HashMap<String,MethodeInfo>());
            DalvikMethod dm = analysis.getExactMethod(c.hashCode(), m.hashCode());
            int numReg = dm.getNumReg() + 1;
            MethodeInfo mi = new MethodeInfo(analysis,c,m,numReg);
            debug.get(c).put(m, mi);
            return mi;
        }
    }
    
    
    private void tabular(final PrintWriter writer,int length){
        writer.println("");
        writer.print("\\begin{tabular}{|l|");
        for (int i = 0; i < length; i++){
            writer.print("c|");
        }
        writer.print("}\n");
        writer.println("\\hline");
    }
    
    private void item(final PrintWriter writer, final boolean bool){
        if (bool)
            writer.print("$\\cbullet$");
        else
            writer.print("$\\times$");
    }
    
    private void tripleline(final PrintWriter writer,final RegInfo reginf, final String linestring){
        writer.print("\\multirow{3}{*}{" + linestring.replace("$", "\\$") + "} ");
        for (int i : reginf.keySet()){
            writer.print(" & ");
            item(writer,reginf.highGet(i));
        }
        writer.print("\\\\ \\cline{2-" + (reginf.keySet().size()+ 1) + "}\n");
        for (int i : reginf.keySet()){
            writer.print(" & ");
            if(analysis.getDebugNumber() >= 2){
                item(writer,reginf.localGet(i));
            }else{
                writer.print("$ $");
            }
        }
        writer.print("\\\\ \\cline{2-" + (reginf.keySet().size() + 1) + "}\n");
        for (int i : reginf.keySet()){
            writer.print(" & ");
            if(analysis.getDebugNumber() >= 3){
                item(writer,reginf.globalGet(i));
            }else{
                writer.print("$ $");
            }
        }
        writer.print("\\\\ \\hline\\hline");
    }
    
    private void newcm(final PrintWriter writer, final String c, final String m){
        writer.print("\\begin{verbatim}\n" + c + " " + m + "\n\\end{verbatim}\n\n");
        DalvikMethod dc = analysis.getExactMethod(c.hashCode(), m.hashCode());
        if (dc == null)
            return;
        writer.println("\\begin{itemize}");
        int codeAddr = 0;
        for (final Instruction inst : dc.getInstructions()){
            if (inst instanceof ReferenceInstruction) {
                ReferenceInstruction referenceInstruction = (ReferenceInstruction)inst;
                Reference reference = referenceInstruction.getReference();
                String referenceString = Utils.getShortReferenceString(reference);
                String referenceStringClass = null;
                if (reference instanceof MethodReference){
                        referenceStringClass = ((MethodReference) reference).getDefiningClass();
                }
                //if ((referenceString != null) && (referenceStringClass != null)){
                    writer.print("\\item $" + codeAddr + " : " + inst.getOpcode().toString().replaceAll("_", "\\\\;\\\\;") + " \\quad " + 
                (referenceStringClass!=null?referenceStringClass.replace("$", "\\$"):"") + 
                            " \\quad " + (referenceString!=null?referenceString.replace("$", "\\$"):"") + "$\n");
                //}
            }
            else{
                writer.print("\\item $" + codeAddr + " : " + inst.getOpcode().toString().replaceAll("_", "\\\\;\\\\;") + "$\n");
            }
            codeAddr += inst.getCodeUnits();
        }
        writer.println("\\end{itemize}");
    }
    
    
    public void printToLatex(){
        if (analysis.isDebugging()){
            System.out.println("Printing debugging information in debug.tex file");
            System.out.println("Just compile wrapper.tex to get the reachabilities tables");
            try{
                final PrintWriter writer = new PrintWriter("./debug.tex", "UTF-8");
                for (String c : debug.keySet()){
                    for (String m : debug.get(c).keySet()){
                        final MethodeInfo minfo = debug.get(c).get(m);
                        this.newcm(writer,c,m);

                        tabular(writer,minfo.regInfo[0].keySet().size());
                        int length = minfo.regInfo.length;
                        writer.print(" $ $");
                        for (int i : minfo.regInfo[0].keySet()){
                            writer.print(" & $" + i + "$ ");
                        }
                        writer.println("\\\\ \\hline");

                        for (int i = 0; i < length ;i++){
                            tripleline(writer,minfo.regInfo[i],"" + i);
                            writer.print("\n");
                        }
                        writer.print("\\end{tabular}\n\n\n");

                        if (analysis.isFlowSens()){
                            tabular(writer,minfo.regInfo[0].keySet().size());
                            writer.print(" $ $");
                            for (int i : minfo.regInfo[0].keySet()){
                                writer.print(" & $" + i + "$ ");
                            }
                            writer.println("\\\\ \\hline");

                            for (final LHKey lhkey : minfo.getLHKeySet()){
                                final LHInfo lhinfo = minfo.getLHInfo(lhkey.instanceNum, lhkey.field);
                                tripleline(writer,lhinfo.getRegInfo(),
                                        "\\begin{tabular}{c}\n" + lhinfo.allocatedClass + "\\\\ \n"+ lhinfo.c + "\\\\ \n" + lhinfo.m + " " + lhinfo.pc + " " + lhinfo.field + "\n\\end{tabular}\n"
                                        );
                                writer.print("\n");
                            }
                            writer.print("\\end{tabular}\n\n\\clearpage\n"); 
                        }
                    }
                }
                writer.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
    }

}

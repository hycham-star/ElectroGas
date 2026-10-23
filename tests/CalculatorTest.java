package dz.electrogas;
import java.nio.file.*;
import java.util.*;

public class CalculatorTest {
    private static int checks;
    private static void near(double actual,double expected,String label) {
        checks++;
        if(!Double.isFinite(actual)||Math.abs(actual-expected)>Math.max(1e-8,Math.abs(expected)*1e-10))throw new AssertionError(label+": "+actual+" != "+expected);
    }
    private static void bad(double[] v,String label) {
        checks++;try {Calculator.calculate(v);}catch(IllegalArgumentException e){return;}throw new AssertionError(label);
    }
    private static void energy(Map<String,Double> out,String sheet,Calculator.Energy e,boolean gas) {
        out.put(sheet+"!E8",e.quantity);
        out.put(sheet+"!G16",e.quantity);out.put(sheet+"!H16",e.energy);
        out.put(sheet+"!E21",e.fixed);out.put(sheet+"!E24",e.energy);out.put(sheet+"!E25",e.ht);out.put(sheet+"!E26",e.base9);out.put(sheet+"!E27",e.base19);
        for(int i=0;i<4;i++){out.put(sheet+"!G"+(12+i),e.quantities[i]);out.put(sheet+"!H"+(12+i),e.amounts[i]);}
    }
    public static void main(String[] args) throws Exception {
        double[] v=Calculator.DEFAULTS.clone();Calculator.Result r=Calculator.calculate(v);
        Map<String,Double> out=new HashMap<>();energy(out,"Électricité",r.electricity,false);energy(out,"Gaz",r.gas,true);
        out.put("Gaz!E7",r.gasM3);
        String[] cells={"E4","E5","E6","E7","E10","E13","E14","E15","E16","E17","E25","E26","E37","E40","E45"};
        double[] values={r.electricity.energy,r.electricity.fixed,r.gas.energy,r.gas.fixed,r.ht,r.base9,r.vat9,r.base19,r.vat19,r.vat,r.electricity.quantity,r.duty,r.net,r.total,r.daily};
        for(int i=0;i<cells.length;i++)out.put("Récapitulatif!"+cells[i],values[i]);
        int fixtureCount=0;
        for(String line:Files.readAllLines(Paths.get("tests/workbook-results.tsv"))) {
            String[] p=line.split("\t");Double actual=out.get(p[0]);
            if(actual==null)throw new AssertionError("Untested workbook formula: "+p[0]);
            near(actual,Double.parseDouble(p[1]),p[0]);fixtureCount++;
        }
        if(fixtureCount!=out.size())throw new AssertionError("Fixture coverage mismatch");
        near(r.total,3157.010877,"Workbook total");
        double[] thresholds={0,70,70.001,125,190,190.001,250,390,390.001,1000,2347};
        for(double q:thresholds) {
            v=Calculator.DEFAULTS.clone();v[1]=0;v[2]=q;r=Calculator.calculate(v);
            near(r.duty,q>390?200:q>190?50:q>70?25:0,"Strict duty boundary "+q);
            double sum=0;for(double n:r.electricity.quantities)sum+=n;near(sum,q,"Electricity allocation "+q);
        }
        v=Calculator.DEFAULTS.clone();v[1]=0;v[2]=2347;r=Calculator.calculate(v);
        near(r.electricity.energy,125*1.7787+125*4.1789+750*4.812+1347*5.4796,"All electricity tiers");
        for(double q:new double[]{0,1125,1125.1,2500,2500.1,7500,7500.1,9000}) {
            v=Calculator.DEFAULTS.clone();v[6]=1;v[7]=0;v[8]=q;r=Calculator.calculate(v);
            double sum=0;for(double n:r.gas.quantities)sum+=n;near(sum,q,"Gas allocation "+q);
            near(r.gas.base19,Math.max(Math.min(q,7500)-2500,0)*.4025+Math.max(q-7500,0)*.4599,"Gas high VAT base");
        }
        v=Calculator.DEFAULTS.clone();v[11]=100;v[13]=-200;v[14]=30;v[15]=40;v[16]=50;r=Calculator.calculate(v);
        near(r.total,3157.010877+100-200+30+40+50,"Fees, negative subsidy and stamp");near(r.vat,383.905577,"Fees preserve workbook VAT rule");
        v=Calculator.DEFAULTS.clone();v[2]=v[1];v[8]=v[7];r=Calculator.calculate(v);near(r.electricity.energy,0,"Zero electricity");near(r.gas.energy,0,"Zero gas");near(r.ht,164.16,"Zero usage retains subscriptions");
        v=Calculator.DEFAULTS.clone();v[5]=1;v[10]=2;r=Calculator.calculate(v);near(r.electricity.fixed,26.22,"Electricity subscription months");near(r.gas.fixed,57,"Gas subscription months");near(r.gas.energy,2181.1735,"Months do not prorate thresholds");
        v=Calculator.DEFAULTS.clone();v[2]=v[1]-1;bad(v,"Reverse electricity index");
        v=Calculator.DEFAULTS.clone();v[8]=v[7]-1;bad(v,"Reverse gas index");
        for(int index:new int[]{3,5,6,10,17}){v=Calculator.DEFAULTS.clone();v[index]=0;bad(v,"Zero divisor or multiplier");}
        v=Calculator.DEFAULTS.clone();v[12]=-1;bad(v,"Negative tax");
        v=Calculator.DEFAULTS.clone();v[0]=Double.NaN;bad(v,"NaN input");v[0]=Double.POSITIVE_INFINITY;bad(v,"Infinite input");
        v=Calculator.DEFAULTS.clone();r=Calculator.calculate(v,true,false);
        near(r.total,531.760662,"Electricity only total");near(r.gas.ht,0,"No gas subscription or energy");near(r.gasM3,0,"No gas volume");near(r.base19,0,"No gas high VAT");near(r.vat9,25.328862,"Electricity VAT only");
        near(r.gasEnabled?1:0,0,"Gas flag");near(r.electricityEnabled?1:0,1,"Electricity flag");
        for(int i=6;i<=10;i++)v[i]=Double.NaN;v[15]=Double.POSITIVE_INFINITY;
        r=Calculator.calculate(v,true,false);near(r.total,531.760662,"Invalid inactive gas fields ignored");near(Double.isNaN(v[6])?1:0,1,"Caller values are preserved");
        v=Calculator.DEFAULTS.clone();r=Calculator.calculate(v,false,true);
        near(r.total,2825.250215,"Gas only total");near(r.electricity.ht,0,"No electricity subscription or energy");near(r.duty,0,"No electricity duty");near(r.vat9,64.882125,"Gas VAT 9 percent");near(r.vat19,293.69459,"Gas VAT 19 percent");
        for(int i=0;i<v.length;i++)if(!Calculator.isInputActive(i,false,true))v[i]=Double.NaN;
        near(Calculator.calculate(v,false,true).total,2825.250215,"Invalid inactive electricity, support, REPE and duty ignored");
        v=Calculator.DEFAULTS.clone();v[13]=-65;v[14]=30;v[15]=67;
        near(Calculator.calculate(v,true,false).total,496.760662,"Electricity support and REPE retained, RGPE excluded");near(Calculator.calculate(v,false,true).total,2892.250215,"Gas RGPE retained, electricity adjustments excluded");
        v=Calculator.DEFAULTS.clone();v[1]=v[2];near(Calculator.calculate(v,true,false).electricity.fixed,78.66,"Zero active consumption still has subscription");
        v=Calculator.DEFAULTS.clone();v[11]=10;v[12]=20;v[16]=5;near(Calculator.calculate(v,true,false).total,366.760662,"Shared fees remain user controlled");
        checks++;try{Calculator.calculate(Calculator.DEFAULTS,false,false);throw new AssertionError("No service must be rejected");}catch(IllegalArgumentException expected){}
        v=Calculator.DEFAULTS.clone();v[2]=v[1]-1;checks++;try{Calculator.calculate(v,true,false);throw new AssertionError("Invalid active electricity must be rejected");}catch(IllegalArgumentException expected){}
        v=Calculator.DEFAULTS.clone();v[8]=v[7]-1;checks++;try{Calculator.calculate(v,false,true);throw new AssertionError("Invalid active gas must be rejected");}catch(IllegalArgumentException expected){}
        System.out.println("PASS: "+checks+" checks, including all "+fixtureCount+" cached workbook formulas and tariff boundaries.");
    }
}

package dz.electrogas;

/** Spreadsheet arithmetic: no intermediate rounding, quarterly tariff thresholds. */
public final class Calculator {
    private Calculator() {}
    public static final String[] KEYS = {"power","eOld","eNew","coefficient","eRate","eMonths","pcs","gOld","gNew","gRate","gMonths","fees","housing","support","repe","rgpe","stamp","days","duty1","duty2","duty3"};
    public static final double[] DEFAULTS = {6,55206,55320,1,4.37,3,9.68,10041,10696,28.5,3,0,200,0,0,0,0,90,25,50,200};
    public static final double[] E_LIMITS = {125,250,1000,999999999};
    public static final double[] G_LIMITS = {1125,2500,7500,999999999};
    public static final double[] E_RATES = {1.7787,4.1789,4.812,5.4796};
    public static final double[] G_RATES = {.1682,.3245,.4025,.4599};

    public static boolean isInputActive(int i,boolean electricity,boolean gas) {
        if(i<=5||i==13||i==14||i>=18)return electricity;
        if((i>=6&&i<=10)||i==15)return gas;
        return true;
    }

    public static final class Energy {
        public final double quantity, fixed, energy, ht, base9, base19;
        public final double[] quantities = new double[4], amounts = new double[4];
        Energy(double consumption, double subscription, double[] limits, double[] rates) {
            quantity=consumption; fixed=subscription;
            double total=0, low=0;
            for(int i=0;i<4;i++) {
                quantities[i]=Math.max(Math.min(consumption,limits[i])-low,0);
                amounts[i]=quantities[i]*rates[i]; total+=amounts[i]; low=limits[i];
            }
            energy=total; ht=energy+fixed; base9=amounts[0]+amounts[1]+fixed; base19=amounts[2]+amounts[3];
        }
    }
    public static final class Result {
        public final Energy electricity, gas;
        public final boolean electricityEnabled, gasEnabled;
        public final double gasM3, ht, base9, base19, vat9, vat19, vat, duty, net, total, daily;
        Result(double[] v,boolean electricityEnabled,boolean gasEnabled) {
            this.electricityEnabled=electricityEnabled;this.gasEnabled=gasEnabled;
            electricity=new Energy(electricityEnabled?(v[2]-v[1])*v[3]:0,electricityEnabled?v[0]*v[4]*v[5]:0,E_LIMITS,E_RATES);
            gasM3=gasEnabled?v[8]-v[7]:0;
            gas=new Energy(gasEnabled?gasM3*v[6]:0,gasEnabled?v[9]*v[10]:0,G_LIMITS,G_RATES);
            ht=electricity.ht+gas.ht+v[11];
            base9=electricity.base9+gas.base9; base19=electricity.base19+gas.base19;
            vat9=base9*.09; vat19=base19*.19; vat=vat9+vat19;
            double q=electricity.quantity;
            duty=q>390?v[20]:q>190?v[19]:q>70?v[18]:0;
            net=ht+vat+duty+v[12]+v[13]+v[14]+v[15]; total=net+v[16]; daily=total/v[17];
        }
    }
    public static Result calculate(double[] v) {
        return calculate(v,true,true);
    }
    public static Result calculate(double[] values,boolean electricity,boolean gas) {
        if(!electricity&&!gas)throw new IllegalArgumentException("Activez au moins un service : électricité ou gaz.");
        if(values.length!=DEFAULTS.length) throw new IllegalArgumentException("Données incomplètes.");
        double[] v=values.clone();
        for(int i=0;i<v.length;i++)if(!isInputActive(i,electricity,gas))v[i]=0;
        for(int i=0;i<v.length;i++) {
            if(Double.isNaN(v[i])||Double.isInfinite(v[i])||Math.abs(v[i])>1e12) throw new IllegalArgumentException("Valeur hors limites.");
            if(i!=13&&v[i]<0) throw new IllegalArgumentException("Les valeurs doivent être positives (sauf le soutien État).");
        }
        if((electricity&&v[2]<v[1])||(gas&&v[8]<v[7])) throw new IllegalArgumentException("Le nouvel index doit être supérieur ou égal à l’ancien.");
        if((electricity&&(v[3]<=0||v[5]<=0))||(gas&&(v[6]<=0||v[10]<=0))||v[17]<=0) throw new IllegalArgumentException("Les coefficients, les mois et les jours doivent être supérieurs à zéro.");
        if((v[2]-v[1])*v[3]>999999999||(v[8]-v[7])*v[6]>999999999) throw new IllegalArgumentException("Consommation hors limites.");
        return new Result(v,electricity,gas);
    }
}

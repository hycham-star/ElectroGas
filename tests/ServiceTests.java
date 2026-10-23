package dz.electrogas.tests;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import dz.electrogas.*;
import java.util.*;

/** Exercises service selection, ignored empty inputs, summary, sharing and persisted choices. */
public final class ServiceTests extends Instrumentation {
    int checks;Activity activity;SharedPreferences prefs;Map<String,?> original;
    @Override public void onCreate(Bundle b){super.onCreate(b);start();}
    void check(boolean b,String label){checks++;if(!b)throw new AssertionError(label);}
    View find(View v,String tag){if(tag.equals(v.getTag()))return v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View x=find(g.getChildAt(i),tag);if(x!=null)return x;}}return null;}
    View view(String tag){return find(activity.getWindow().getDecorView(),tag);}
    void click(String tag){runOnMainSync(()->{View v=view(tag);if(v==null)throw new AssertionError("Missing "+tag);v.performClick();});waitForIdleSync();}
    void input(int i,String value){runOnMainSync(()->((EditText)view("calculator.input."+i)).setText(value));waitForIdleSync();}
    String total(){String[] s={""};runOnMainSync(()->s[0]=((TextView)view("calculator.total")).getText().toString().replaceAll("\\s|\\u00a0|\\u202f",""));return s[0];}
    String text(View v){StringBuilder b=new StringBuilder();if(v instanceof TextView)b.append(((TextView)v).getText()).append('\n');if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)b.append(text(g.getChildAt(i)));}return b.toString();}
    String screen(){String[] s={""};runOnMainSync(()->s[0]=text(activity.getWindow().getDecorView()));return s[0];}
    boolean present(String tag){boolean[] found={false};runOnMainSync(()->found[0]=view(tag)!=null);return found[0];}
    void launch(){activity=startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();}
    String share(){String[] captured={null};ActivityMonitor monitor=new ActivityMonitor(){@Override public ActivityResult onStartActivity(Intent i){if(!Intent.ACTION_CHOOSER.equals(i.getAction()))return null;Intent payload=i.getParcelableExtra(Intent.EXTRA_INTENT);captured[0]=payload.getStringExtra(Intent.EXTRA_TEXT);return new ActivityResult(Activity.RESULT_CANCELED,null);}};addMonitor(monitor);click("calculator.share");removeMonitor(monitor);if(captured[0]==null)throw new AssertionError("No share intent");return captured[0];}
    @Override public void onStart(){Bundle result=new Bundle();int code=Activity.RESULT_OK;try {
        prefs=getTargetContext().getSharedPreferences("electrogas",0);original=prefs.getAll();SharedPreferences.Editor seed=prefs.edit();for(int i=0;i<Calculator.KEYS.length;i++)seed.putString(Calculator.KEYS[i],Double.toString(Calculator.DEFAULTS[i]));seed.putBoolean("electricityEnabled",true).putBoolean("gasEnabled",true).putInt("page",0).commit();
        launch();check(total().equals("3157,01DA"),"existing combined default remains unchanged");
        click("calculator.tab.1");input(6,"");input(7,"");input(8,"");check(total().equals("—DA"),"empty active gas is validated");
        click("calculator.gas");check(total().equals("531,76DA"),"electricity-only total with blank gas");check(!present("calculator.input.7"),"inactive gas fields hidden");check(!prefs.getBoolean("gasEnabled",true),"gas choice saved");
        click("calculator.tab.3");String summary=screen();check(summary.contains("Énergie électrique HT"),"electricity included in summary");check(!summary.contains("Énergie gaz HT")&&!summary.contains("Abonnement gaz HT")&&!summary.contains("RGPE"),"gas and gas charges excluded from summary");
        String shared=share();check(shared.contains("Électricité :")&&!shared.contains("Gaz :")&&shared.contains("531,76"),"electricity-only share text");
        runOnMainSync(()->activity.finish());launch();check(total().equals("531,76DA"),"disabled gas and ignored blanks persist after reopen");
        click("calculator.tab.1");click("calculator.gas");check(total().equals("—DA"),"reactivating gas restores and validates blank inputs");input(6,"9,68");input(7,"10041");input(8,"10696");check(total().equals("3157,01DA"),"both services can be restored");
        click("calculator.tab.0");input(1,"");input(2,"");click("calculator.electricity");check(total().equals("2825,25DA"),"gas-only total with blank electricity");check(!present("calculator.input.1"),"inactive electricity fields hidden");
        click("calculator.tab.2");check(!present("calculator.input.13")&&!present("calculator.input.14")&&!present("calculator.input.18"),"electricity support, REPE and duty fields hidden");check(present("calculator.input.15"),"gas RGPE remains editable");
        click("calculator.tab.3");summary=screen();check(summary.contains("Énergie gaz HT")&&!summary.contains("Abonnement électrique HT")&&!summary.contains("Droit fixe")&&!summary.contains("REPE"),"gas-only summary excludes electricity fees");
        shared=share();check(shared.contains("Gaz :")&&!shared.contains("Électricité :")&&shared.replaceAll("\\s|\\u00a0|\\u202f","").contains("2825,25"),"gas-only share text: "+shared);
        click("calculator.tab.1");click("calculator.gas");check(total().equals("—DA"),"no service selected cannot create a bill");check(screen().contains("Activez l’électricité ou le gaz pour calculer."),"no-service instruction shown");check(present("main.invoices"),"invoice archive still available without services");
        result.putString("result","PASS: "+checks+" optional service checks (electricity only, gas only, hidden fields, summary, sharing and persistence).");
    }catch(Throwable e){code=Activity.RESULT_CANCELED;result.putString("result","FAIL: "+e);android.util.Log.e("ServiceTests","Failed",e);}finally{
        if(activity!=null)runOnMainSync(()->activity.finish());if(original!=null){SharedPreferences.Editor e=prefs.edit().clear();for(Map.Entry<String,?> v:original.entrySet()){Object o=v.getValue();if(o instanceof String)e.putString(v.getKey(),(String)o);else if(o instanceof Boolean)e.putBoolean(v.getKey(),(Boolean)o);else if(o instanceof Integer)e.putInt(v.getKey(),(Integer)o);else if(o instanceof Long)e.putLong(v.getKey(),(Long)o);else if(o instanceof Float)e.putFloat(v.getKey(),(Float)o);else if(o instanceof Set){Set<String> strings=new HashSet<>();for(Object s:(Set<?>)o)strings.add((String)s);e.putStringSet(v.getKey(),strings);}}e.commit();}
    }finish(code,result);}
}

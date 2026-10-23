package dz.electrogas;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.android.gms.ads.*;
import com.google.ads.mediation.admob.AdMobAdapter;
import com.google.android.ump.*;

/** Deliberately SAMPLE-ONLY. Changing IDs alone must never enable live monetization. */
final class TestAds {
    static final String APP_ID="ca-app-pub-3940256099942544~3347511713";
    static final String BANNER_ID="ca-app-pub-3940256099942544/9214589741";
    private final Activity activity;
    private final SharedPreferences preferences;
    private LinearLayout slot;
    private AdView banner;
    private TextView label;
    private boolean initialized, initializing, destroyed, resumed, loaded;
    private int generation;

    TestAds(Activity activity) {
        this.activity=activity;
        preferences=activity.getSharedPreferences("ad_privacy",Activity.MODE_PRIVATE);
    }
    boolean enabled(){return preferences.getBoolean("google_test_ads",false);}
    private boolean sampleApp(){
        try {
            Bundle data=activity.getPackageManager().getApplicationInfo(activity.getPackageName(),PackageManager.GET_META_DATA).metaData;
            return data!=null&&APP_ID.equals(data.getString("com.google.android.gms.ads.APPLICATION_ID"));
        } catch(PackageManager.NameNotFoundException e){return false;}
    }
    void attach(LinearLayout target){
        slot=target;
        if(banner!=null){
            ViewGroup old=(ViewGroup)banner.getParent();if(old!=null)old.removeView(banner);
            ViewGroup oldLabel=(ViewGroup)label.getParent();if(oldLabel!=null)oldLabel.removeView(label);
            target.addView(label);target.addView(banner);
        }
        target.setVisibility(enabled()&&loaded?View.VISIBLE:View.GONE);
    }
    void start(){if(enabled())prepare();}
    void settings(){
        String message=(enabled()?"Tests Google : activés.\n\n":"Tests Google : désactivés.\n\n")+"Le calculateur et vos factures fonctionnent sans publicité et sans Internet.\n\n"
            +"Cette version utilise uniquement les identifiants de démonstration Google. Aucun revenu n’est généré.\n\n"
            +"Activer le test autorise une connexion à Google (adresse IP, informations sur l’appareil et l’application, interactions publicitaires). Aucun index, montant, photo ou PDF de facture n’est transmis par ElectroGas.\n\n"
            +"Vous pouvez désactiver les tests ici à tout moment. Cela arrête les prochaines demandes, sans effacer les données déjà reçues par Google. L’identifiant publicitaire Android n’est pas demandé par cette version.";
        AlertDialog.Builder dialog=new AlertDialog.Builder(activity).setTitle("Publicités de test · Confidentialité")
            .setMessage(message).setNegativeButton("Fermer",null)
            .setNeutralButton("Données et confidentialité",(d,w)->privacy());
        if(enabled())dialog.setPositiveButton("Désactiver les tests",(d,w)->setEnabled(false));
        else dialog.setPositiveButton("Activer les tests Google",(d,w)->setEnabled(true));
        dialog.show();
    }
    void privacy(){
        String message="Vos saisies, photos et PDF sont conservés dans l’espace privé de l’application. Aucun serveur ElectroGas ne reçoit ces documents. Les sauvegardes et partages sont envoyés uniquement vers l’emplacement ou l’application que vous choisissez. Les ZIP exportés ne sont pas chiffrés.\n\n"
            +"Supprimez une facture depuis son menu pour effacer sa copie locale. Les copies exportées restent à supprimer dans leur destination. Effacer les données Android ou désinstaller supprime l’historique local : sauvegardez-le d’abord.\n\n"
            +"Les publicités Google sont facultatives, désactivées par défaut et limitées aux annonces de test. Le calcul et le carnet restent disponibles si vous refusez. Google peut traiter des informations techniques lorsque vous activez les tests. Sa politique est disponible sur https://policies.google.com/privacy .\n\n"
            +"Le formulaire Google, lorsqu’il est requis et configuré, est accessible ci-dessous. Cette démonstration ne remplace pas la configuration du consentement pour une publication commerciale.";
        AlertDialog.Builder d=new AlertDialog.Builder(activity).setTitle("Vos données").setMessage(message).setNegativeButton("Fermer",null);
        ConsentInformation info=UserMessagingPlatform.getConsentInformation(activity);
        if(info.getPrivacyOptionsRequirementStatus()==ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED)
            d.setPositiveButton("Choix Google",(x,w)->UserMessagingPlatform.showPrivacyOptionsForm(activity,error->{
                // Stop existing requests after any change. A new local opt-in is required.
                setEnabled(false);
                if(error!=null)notice("Formulaire indisponible. Les publicités de test sont désactivées.");
            }));
        d.show();
    }
    void setEnabled(boolean value){
        preferences.edit().putBoolean("google_test_ads",value).apply();
        generation++;
        if(!value){removeBanner();return;}
        android.widget.Toast.makeText(activity,"Tests activés. L’annonce dépend de la connexion et de la disponibilité Google.",android.widget.Toast.LENGTH_LONG).show();
        prepare();
    }
    private void prepare(){
        if(destroyed||!enabled()||initializing||banner!=null)return;
        if(!sampleApp()){notice("Configuration de test invalide : aucune publicité demandée.");return;}
        initializing=true;final int requestGeneration=generation;
        ConsentInformation info=UserMessagingPlatform.getConsentInformation(activity);
        info.requestConsentInfoUpdate(activity,new ConsentRequestParameters.Builder().build(),()->{
            if(!valid(requestGeneration)){initializing=false;if(enabled())prepare();return;}
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity,error->sampleOnly(requestGeneration));
        },error->sampleOnly(requestGeneration));
    }
    private boolean valid(int token){return !destroyed&&enabled()&&token==generation&&sampleApp();}
    private void sampleOnly(int token){
        // UMP cannot serve publisher messages for Google's sample account. This fallback is
        // ONLY for the fixed Google demo app + unit above, following explicit local opt-in.
        // Live IDs require a separate fail-closed canRequestAds() implementation; see docs.
        if(!valid(token)){initializing=false;if(enabled())prepare();return;}
        if(initialized){initializing=false;load(token);return;}
        MobileAds.initialize(activity,status->activity.runOnUiThread(()->{
            initialized=true;initializing=false;if(valid(token))load(token);else if(enabled())prepare();
        }));
    }
    private void load(int token){
        if(!valid(token)||banner!=null||slot==null)return;
        slot.post(()->{
            if(!valid(token)||banner!=null)return;
            View parent=(View)slot.getParent();
            int px=parent.getWidth()-parent.getPaddingLeft()-parent.getPaddingRight()-slot.getPaddingLeft()-slot.getPaddingRight();if(px<=0)return;
            int width=(int)(px/activity.getResources().getDisplayMetrics().density);
            label=new TextView(activity);label.setText("Annonce de test Google · aucun revenu");label.setTextSize(11);label.setGravity(android.view.Gravity.CENTER);
            banner=new AdView(activity);banner.setTag("ads.banner");banner.setAdUnitId(BANNER_ID);
            banner.setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity,width));
            banner.setAdListener(new AdListener(){
                @Override public void onAdLoaded(){if(!valid(token))return;loaded=true;if(resumed)slot.setVisibility(View.VISIBLE);}
                @Override public void onAdFailedToLoad(LoadAdError error){if(!valid(token))return;loaded=false;if(slot!=null)slot.setVisibility(View.GONE);}
            });
            slot.addView(label);slot.addView(banner);slot.setVisibility(View.GONE);
            Bundle extras=new Bundle();extras.putString("npa","1");
            banner.loadAd(new AdRequest.Builder().addNetworkExtrasBundle(AdMobAdapter.class,extras).build());
        });
    }
    private void notice(String text){if(!destroyed)new AlertDialog.Builder(activity).setMessage(text).setPositiveButton("OK",null).show();}
    private void removeBanner(){
        loaded=false;
        if(banner!=null){banner.destroy();ViewGroup parent=(ViewGroup)banner.getParent();if(parent!=null)parent.removeView(banner);banner=null;}
        if(slot!=null){slot.removeAllViews();slot.setVisibility(View.GONE);}label=null;
    }
    void resume(){resumed=true;if(banner!=null){banner.resume();if(loaded&&slot!=null)slot.setVisibility(View.VISIBLE);}}
    void pause(){resumed=false;if(banner!=null)banner.pause();}
    void destroy(){destroyed=true;generation++;removeBanner();slot=null;}
}

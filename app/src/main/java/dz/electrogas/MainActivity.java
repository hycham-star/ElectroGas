package dz.electrogas;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Build;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.WindowInsets;
import android.widget.*;
import java.text.NumberFormat;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int INK=0xff123a3e, TEAL=0xff087e72, MUTED=0xff596d70, BG=0xfff3f6f5;
    private final String[] raw=new String[Calculator.KEYS.length];
    private final EditText[] fields=new EditText[Calculator.KEYS.length];
    private final NumberFormat money=NumberFormat.getNumberInstance(Locale.FRANCE);
    private LinearLayout body, root;
    private TextView amount, status;
    private SharedPreferences prefs;
    private int page=0;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        money.setMinimumFractionDigits(2); money.setMaximumFractionDigits(2);
        prefs=getSharedPreferences("electrogas",MODE_PRIVATE);
        for(int i=0;i<raw.length;i++) raw[i]=prefs.getString(Calculator.KEYS[i],plain(Calculator.DEFAULTS[i]));
        page=state==null?prefs.getInt("page",0):state.getInt("page",0);
        render();
    }
    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); out.putInt("page",page); }
    private int dp(float v) { return (int)(v*getResources().getDisplayMetrics().density+.5f); }
    private static String plain(double v) { return v==(long)v?Long.toString((long)v):Double.toString(v); }
    private String fmt(double n) { return money.format(n); }
    private GradientDrawable bg(int color,int radius) { GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private TextView text(String s,int size,int color,boolean bold) {
        TextView t=new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(color);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        t.setPadding(0,dp(4),0,dp(4)); return t;
    }
    private LinearLayout vertical() { LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v; }
    private LinearLayout card() {
        LinearLayout c=vertical(); c.setPadding(dp(18),dp(14),dp(18),dp(14));c.setBackground(bg(Color.WHITE,18));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(14));body.addView(c,lp);return c;
    }
    private Button button(String label,boolean primary) {
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(14);b.setMinHeight(dp(48));
        b.setTextColor(primary?Color.WHITE:TEAL);b.setBackground(bg(primary?TEAL:0xffe3efeb,12));
        b.setPadding(dp(12),dp(8),dp(12),dp(8));return b;
    }
    private void render() {
        prefs.edit().putInt("page",page).apply();
        for(int i=0;i<fields.length;i++)fields[i]=null;
        root=vertical();root.setBackgroundColor(BG);setContentView(root);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            if(Build.VERSION.SDK_INT>=30) {
                android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
                root.setPadding(bars.left,bars.top,bars.right,bars.bottom);
            } else applyLegacyInsets(insets);
            return insets;
        });
        root.requestApplyInsets();
        LinearLayout header=vertical();header.setPadding(dp(22),dp(14),dp(22),dp(16));header.setBackgroundColor(0xff092d32);root.addView(header);
        header.addView(text("ELECTROGAS",13,0xff80dfc7,true));
        header.addView(text("Votre facture, en clair.",25,Color.WHITE,true));
        header.addView(text("Estimation Sonelgaz · 54M / 23M",13,0xffbdcfd0,false));
        amount=text("",32,Color.WHITE,true);header.addView(amount);
        status=text("",12,0xffbdcfd0,false);header.addView(status);
        Button archive=button("Mes factures · Photos et historique",false);archive.setTag("main.invoices");header.addView(archive);archive.setOnClickListener(v->startActivity(new Intent(this,InvoiceActivity.class)));
        LinearLayout tabs=new LinearLayout(this);tabs.setPadding(dp(10),dp(12),dp(10),dp(10));root.addView(tabs);
        String[] names={"Électricité","Gaz","Frais","Total"};
        for(int i=0;i<names.length;i++) {
            final int target=i;Button b=button(names[i],page==i);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(dp(3),0,dp(3),0);tabs.addView(b,lp);
            b.setTextSize(12);b.setPadding(dp(3),dp(6),dp(3),dp(6));b.setOnClickListener(v->{if(target!=3||validate()){page=target;render();}});
        }
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        body=vertical();body.setPadding(dp(16),dp(6),dp(16),dp(24));scroll.addView(body);
        if(page==0) electricity(); else if(page==1) gas(); else if(page==2) fees(); else summary();
        LinearLayout foot=card();
        foot.addView(text("Vos données restent sur cet appareil.",12,MUTED,false));
        Button about=button("À propos du calcul",false);foot.addView(about);about.setOnClickListener(v->about());
        Button reset=button("Réinitialiser les valeurs d’exemple",false);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.topMargin=dp(10);foot.addView(reset,rp);
        reset.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Réinitialiser ?").setMessage("Vos saisies seront remplacées par les valeurs du fichier Excel.")
            .setNegativeButton("Annuler",null).setPositiveButton("Réinitialiser",(d,w)->{for(int i=0;i<raw.length;i++)raw[i]=plain(Calculator.DEFAULTS[i]);save();render();}).show());
        update();
    }
    @SuppressWarnings("deprecation") private void applyLegacyInsets(WindowInsets insets) {
        root.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
    }
    private void field(LinearLayout parent,String label,int index,String hint) {
        parent.addView(text(label,14,INK,true));
        EditText e=new EditText(this);fields[index]=e;e.setSingleLine(true);e.setTextSize(18);e.setTextColor(TEAL);
        e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|(index==13?InputType.TYPE_NUMBER_FLAG_SIGNED:0));
        e.setBackground(bg(0xffeef5f2,10));e.setPadding(dp(12),dp(10),dp(12),dp(10));e.setMinimumHeight(dp(50));
        e.setText(raw[index]);e.setContentDescription(label);parent.addView(e,new LinearLayout.LayoutParams(-1,-2));
        if(hint!=null)parent.addView(text(hint,12,MUTED,false));
        e.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){raw[index]=s.toString();save();update();}
            public void afterTextChanged(Editable s){}
        });
    }
    private void title(LinearLayout c,String title,String caption) {c.addView(text(title,20,INK,true));if(caption!=null)c.addView(text(caption,13,MUTED,false));}
    private void electricity() {
        LinearLayout c=card();title(c,"01  /  Électricité","Saisissez les index de votre compteur, en kWh.");
        field(c,"Ancien index · kWh",1,null);field(c,"Nouvel index · kWh",2,null);
        field(c,"Coefficient de lecture",3,"Habituellement 1. À vérifier sur votre facture.");
        c=card();title(c,"Abonnement","Les seuils du fichier sont trimestriels ; modifier les mois ne les ajuste pas.");
        field(c,"Puissance souscrite · kW",0,null);field(c,"Redevance · DA/kW/mois",4,null);field(c,"Période d’abonnement · mois",5,null);
        next("Continuer avec le gaz",1);
    }
    private void gas() {
        LinearLayout c=card();title(c,"02  /  Gaz","Les m³ sont convertis en thermies avec le PCS.");
        field(c,"Ancien index · m³",7,null);field(c,"Nouvel index · m³",8,null);field(c,"PCS · Th/m³",6,"Reprenez le pouvoir calorifique de votre facture.");
        c=card();title(c,"Abonnement gaz",null);field(c,"Forfait · DA/mois",9,null);field(c,"Période d’abonnement · mois",10,null);
        next("Continuer avec les frais",2);
    }
    private void fees() {
        LinearLayout c=card();title(c,"03  /  Frais et ajustements","Reportez les montants de votre facture, en DA.");
        field(c,"Frais et prestations HT",11,"La formule Excel n’ajoute pas de TVA sur ce poste.");field(c,"Taxe d’habitation",12,null);
        field(c,"Contribution / soutien État",13,"Saisissez un montant négatif pour une réduction. Aucune aide n’est appliquée automatiquement.");
        field(c,"REPE · électricité",14,null);field(c,"RGPE · gaz",15,null);field(c,"Timbre · espèces",16,"Laissez 0 pour un autre moyen de paiement.");
        c=card();title(c,"Droit fixe sur consommation","Seuils stricts du fichier : >70, >190 et >390 kWh.");
        field(c,"Plus de 70 à 190 kWh · DA",18,null);field(c,"Plus de 190 à 390 kWh · DA",19,null);field(c,"Plus de 390 kWh · DA",20,"200 DA dans le fichier ; ce montant doit être vérifié sur votre facture.");
        c=card();title(c,"Indicateur quotidien",null);field(c,"Durée de la période · jours",17,null);next("Voir le récapitulatif",3);
    }
    private void next(String label,int target) {Button b=button(label,true);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(16);body.addView(b,lp);b.setOnClickListener(v->{if(validate()){page=target;render();}});}
    private double[] inputs() {
        double[] v=new double[raw.length];
        for(int i=0;i<raw.length;i++) {
            String s=raw[i].trim().replace(',','.');
            if(!s.matches("-?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)"))throw new IllegalArgumentException("Saisissez un nombre valide dans chaque champ.");
            v[i]=Double.parseDouble(s);
        }return v;
    }
    private void save(){SharedPreferences.Editor e=prefs.edit();for(int i=0;i<raw.length;i++)e.putString(Calculator.KEYS[i],raw[i]);e.apply();}
    private void update() {
        if(amount==null)return;
        try{Calculator.Result r=Calculator.calculate(inputs());amount.setText(fmt(r.total)+" DA");status.setText(fmt(r.electricity.quantity)+" kWh  ·  "+fmt(r.gas.quantity)+" Th  ·  estimation TTC");}
        catch(IllegalArgumentException ex){amount.setText("— DA");status.setText("Vérifiez les valeurs saisies pour calculer.");}
    }
    private boolean validate() {
        try{Calculator.calculate(inputs());return true;}
        catch(IllegalArgumentException ex){new AlertDialog.Builder(this).setTitle("Vérifiez votre saisie").setMessage(ex.getMessage()).setPositiveButton("Corriger",null).show();return false;}
    }
    private void row(LinearLayout parent,String label,double value,String unit) {
        LinearLayout line=new LinearLayout(this);line.setGravity(Gravity.CENTER_VERTICAL);line.setPadding(0,dp(5),0,dp(5));
        TextView l=text(label,14,MUTED,false);line.addView(l,new LinearLayout.LayoutParams(0,-2,1));
        TextView r=text(fmt(value)+" "+unit,14,INK,true);r.setGravity(Gravity.END);line.addView(r);parent.addView(line);
    }
    private void summary() {
        Calculator.Result r;
        try{r=Calculator.calculate(inputs());}catch(IllegalArgumentException ex){LinearLayout c=card();title(c,"Données à corriger",ex.getMessage());return;}
        LinearLayout c=card();title(c,"04  /  Votre estimation","Calcul identique au classeur fourni. Montants arrondis uniquement à l’affichage.");
        row(c,"Énergie électrique HT",r.electricity.energy,"DA");row(c,"Abonnement électrique HT",r.electricity.fixed,"DA");
        row(c,"Énergie gaz HT",r.gas.energy,"DA");row(c,"Abonnement gaz HT",r.gas.fixed,"DA");row(c,"Frais HT",inputs()[11],"DA");row(c,"Total hors taxes",r.ht,"DA");
        c=card();title(c,"TVA et autres montants",null);row(c,"Base TVA 9 %",r.base9,"DA");row(c,"TVA 9 %",r.vat9,"DA");row(c,"Base TVA 19 %",r.base19,"DA");row(c,"TVA 19 %",r.vat19,"DA");row(c,"Total TVA",r.vat,"DA");row(c,"Droit fixe",r.duty,"DA");
        double[] v=inputs();row(c,"Taxe d’habitation",v[12],"DA");row(c,"Soutien / contribution",v[13],"DA");row(c,"REPE",v[14],"DA");row(c,"RGPE",v[15],"DA");row(c,"Net TTC avant timbre",r.net,"DA");row(c,"Timbre",v[16],"DA");
        c=card();title(c,"Total à payer",null);TextView total=text(fmt(r.total)+" DA",30,TEAL,true);c.addView(total);row(c,"Moyenne par jour",r.daily,"DA/jour");
        Button share=button("Partager l’estimation",true);c.addView(share);share.setOnClickListener(w->{Intent intent=new Intent(Intent.ACTION_SEND);intent.setType("text/plain");intent.putExtra(Intent.EXTRA_TEXT,shareText(r));startActivity(Intent.createChooser(intent,"Partager l’estimation"));});
        breakdown("Électricité · détail par tranche",r.electricity,Calculator.E_RATES,"kWh");breakdown("Gaz · détail par tranche",r.gas,Calculator.G_RATES,"Th");
        c=card();row(c,"Volume de gaz",r.gasM3,"m³");
    }
    private void breakdown(String title,Calculator.Energy e,double[] rates,String unit) {
        LinearLayout c=card();title(c,title,"Quantité × tarif HT = montant HT");
        for(int i=0;i<4;i++) {
            c.addView(text("Tranche "+(i+1)+" · TVA "+(i<2?"9":"19")+" %",14,INK,true));
            c.addView(text(fmt(e.quantities[i])+" "+unit+" × "+String.format(Locale.FRANCE,"%.4f",rates[i])+" = "+fmt(e.amounts[i])+" DA",13,MUTED,false));
        }
    }
    private String shareText(Calculator.Result r) {
        return "ElectroGas — Estimation Sonelgaz\nÉlectricité : "+fmt(r.electricity.quantity)+" kWh\nGaz : "+fmt(r.gas.quantity)+" Th\nTotal HT : "+fmt(r.ht)+" DA\nTVA : "+fmt(r.vat)+" DA\nTotal à payer : "+fmt(r.total)+" DA\nMoyenne : "+fmt(r.daily)+" DA/jour\nSimulation selon le fichier Calculateur_Facture_Sonelgaz.xlsx ; ne remplace pas une facture officielle.";
    }
    private void about() {
        new AlertDialog.Builder(this).setTitle("À propos d’ElectroGas").setMessage(
            "Application indépendante, utilisable sans Internet.\n\nCalculs et valeurs initiales repris du fichier Calculateur_Facture_Sonelgaz.xlsx. Les notes d’exemple du fichier diffèrent de ses cellules actuelles : l’application utilise les cellules (114 kWh et 6 340,40 Th).\n\nTarifs du fichier : électricité 54M et gaz 23M, décision CREG D/22-15/CD citée par le classeur. Tarifs non vérifiés en temps réel.\n\nSeuils trimestriels fixes, même si vous modifiez la durée d’abonnement. Compteur unique sur toute la période. Pour un remplacement de compteur, le prorata n’est pas géré.\n\nLe droit fixe au-delà de 390 kWh est réglé à 200 DA comme dans le fichier ; vérifiez-le sur votre facture. Les aides, frais, taxes et le timbre restent à saisir. Une réduction doit être négative.\n\nSource citée : creg.gov.dz/fr/consommateurs/comment-lire-votre-facture/\n\nCette estimation ne remplace pas une facture officielle et l’application n’est pas affiliée à Sonelgaz.")
            .setPositiveButton("Compris",null).show();
    }
}

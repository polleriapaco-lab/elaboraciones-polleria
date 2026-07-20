package es.lapolleria.elaboraciones;

import android.app.*;
import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
  static final int ORANGE=Color.rgb(255,106,0), BLACK=Color.rgb(20,20,20);
  static final long DAY=24L*60*60*1000, SHELF_LIFE=6*DAY;
  static final String CHANNEL="caducidades";
  final SimpleDateFormat dateTime=new SimpleDateFormat("dd/MM/yyyy · HH:mm",new Locale("es","ES"));
  final SimpleDateFormat dateOnly=new SimpleDateFormat("dd/MM/yyyy",new Locale("es","ES"));
  Db db; LinearLayout content,undoBar; long lastRecord=-1;
  Handler handler=new Handler(Looper.getMainLooper()); Runnable hideUndo=()->undoBar.setVisibility(View.GONE);

  @Override public void onCreate(Bundle b){super.onCreate(b);db=new Db(this);createChannel();requestNotifications();build();showProducts();}
  void createChannel(){if(Build.VERSION.SDK_INT>=26){NotificationChannel c=new NotificationChannel(CHANNEL,"Avisos de caducidad",NotificationManager.IMPORTANCE_HIGH);c.setDescription("Avisa un día antes de que caduquen los elaborados");getSystemService(NotificationManager.class).createNotificationChannel(c);}}
  void requestNotifications(){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},100);}
  int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
  TextView text(String s,int size,int color){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
  GradientDrawable bg(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
  Button tab(String s){Button b=new Button(this);b.setText(s);b.setTextSize(14);b.setTextColor(Color.WHITE);b.setBackgroundColor(Color.TRANSPARENT);b.setMinWidth(dp(130));return b;}

  void build(){
    LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.rgb(246,246,246));
    LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(22),dp(12),dp(22),dp(12));top.setBackgroundColor(BLACK);
    TextView logo=text("LA POLLERÍA 2.0",23,Color.WHITE);logo.setTypeface(Typeface.DEFAULT,Typeface.BOLD);top.addView(logo,new LinearLayout.LayoutParams(0,dp(52),1));
    Button products=tab("PRODUCTOS");products.setOnClickListener(v->showProducts());top.addView(products);
    Button history=tab("HISTÓRICO");history.setOnClickListener(v->showHistory(0));top.addView(history);
    Button add=tab("+ PRODUCTO");add.setOnClickListener(v->addProductDialog());top.addView(add);root.addView(top);
    FrameLayout frame=new FrameLayout(this);content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(18),dp(16),dp(18),dp(16));frame.addView(content,new FrameLayout.LayoutParams(-1,-1));
    undoBar=new LinearLayout(this);undoBar.setGravity(Gravity.CENTER_VERTICAL);undoBar.setPadding(dp(18),dp(8),dp(10),dp(8));undoBar.setBackground(bg(BLACK,12));undoBar.setVisibility(View.GONE);
    TextView msg=text("Elaboración guardada",17,Color.WHITE);undoBar.addView(msg,new LinearLayout.LayoutParams(0,dp(48),1));Button undo=new Button(this);undo.setText("DESHACER");undo.setTextColor(ORANGE);undo.setBackgroundColor(Color.TRANSPARENT);undo.setOnClickListener(v->undoLast());undoBar.addView(undo);
    FrameLayout.LayoutParams up=new FrameLayout.LayoutParams(dp(430),dp(66),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);up.setMargins(0,0,0,dp(16));frame.addView(undoBar,up);root.addView(frame,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
  }

  void clear(){content.removeAllViews();}
  void showProducts(){
    clear();TextView title=text("Pulsa un producto para guardar elaboración · Caducidad automática: 6 días",22,BLACK);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);title.setPadding(dp(4),0,0,dp(12));content.addView(title,new LinearLayout.LayoutParams(-1,dp(52)));
    ScrollView scroll=new ScrollView(this);GridLayout grid=new GridLayout(this);grid.setColumnCount(4);grid.setPadding(0,0,0,dp(90));
    for(Product p:db.products()){
      LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setGravity(Gravity.CENTER);card.setPadding(dp(8),dp(10),dp(8),dp(10));card.setBackground(bg(Color.WHITE,14));
      TextView icon=text(p.icon,34,BLACK);icon.setGravity(Gravity.CENTER);card.addView(icon,new LinearLayout.LayoutParams(-1,dp(50)));
      TextView name=text(p.name,16,BLACK);name.setGravity(Gravity.CENTER);name.setTypeface(Typeface.DEFAULT,Typeface.BOLD);card.addView(name,new LinearLayout.LayoutParams(-1,0,1));
      Long last=db.last(p.id);String info="Sin registros";int infoColor=Color.DKGRAY;if(last!=null){long expiry=last+SHELF_LIFE,remaining=expiry-System.currentTimeMillis();info="Caduca: "+dateOnly.format(new Date(expiry));if(remaining<=0)infoColor=Color.RED;else if(remaining<=DAY)infoColor=ORANGE;}TextView latest=text(info,12,infoColor);latest.setGravity(Gravity.CENTER);latest.setTypeface(Typeface.DEFAULT,Typeface.BOLD);card.addView(latest,new LinearLayout.LayoutParams(-1,dp(30)));
      card.setClickable(true);card.setForeground(getDrawable(android.R.drawable.list_selector_background));card.setOnClickListener(v->record(p));card.setOnLongClickListener(v->{showHistory(p.id);return true;});
      GridLayout.LayoutParams gp=new GridLayout.LayoutParams();gp.width=0;gp.height=dp(150);gp.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);gp.setMargins(dp(6),dp(6),dp(6),dp(6));grid.addView(card,gp);
    }scroll.addView(grid);content.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  }
  void record(Product p){long now=System.currentTimeMillis();lastRecord=db.addRecord(p.id,now);scheduleExpiry(this,lastRecord,p.name,now);((TextView)undoBar.getChildAt(0)).setText("✓ "+p.name+" · caduca "+dateOnly.format(new Date(now+SHELF_LIFE)));handler.removeCallbacks(hideUndo);handler.postDelayed(hideUndo,6000);showProducts();undoBar.setVisibility(View.VISIBLE);}
  void undoLast(){if(lastRecord>0){db.deleteRecord(lastRecord);cancelExpiry(this,lastRecord);}lastRecord=-1;undoBar.setVisibility(View.GONE);showProducts();}

  void showHistory(long selected){
    clear();LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);TextView title=text("Histórico de elaboraciones",22,BLACK);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);head.addView(title,new LinearLayout.LayoutParams(0,dp(60),1));
    Spinner filter=new Spinner(this);List<Product> ps=db.products();List<String> labels=new ArrayList<>();labels.add("Todos los productos");int sel=0;for(int i=0;i<ps.size();i++){labels.add(ps.get(i).name);if(ps.get(i).id==selected)sel=i+1;}
    filter.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels));filter.setSelection(sel,false);head.addView(filter,new LinearLayout.LayoutParams(dp(350),dp(56)));content.addView(head);
    LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);ScrollView scroll=new ScrollView(this);scroll.addView(list);content.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
    filter.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){}public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){renderHistory(list,pos==0?0:ps.get(pos-1).id);}});renderHistory(list,selected);
  }
  void renderHistory(LinearLayout list,long product){
    list.removeAllViews();List<Record> rows=db.records(product);if(rows.isEmpty()){TextView e=text("Todavía no hay elaboraciones registradas.",18,Color.DKGRAY);e.setGravity(Gravity.CENTER);list.addView(e,new LinearLayout.LayoutParams(-1,dp(140)));return;}
    for(Record r:rows){long expiry=r.time+SHELF_LIFE,remaining=expiry-System.currentTimeMillis();int c=remaining<=0?Color.RED:(remaining<=DAY?ORANGE:BLACK);LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(18),dp(10),dp(18),dp(10));row.setBackgroundColor(Color.WHITE);TextView n=text(r.icon+"   "+r.name,17,BLACK);n.setTypeface(Typeface.DEFAULT,Typeface.BOLD);row.addView(n,new LinearLayout.LayoutParams(0,dp(58),1));TextView d=text("Elaborado: "+dateTime.format(new Date(r.time))+"\nCaduca: "+dateTime.format(new Date(expiry)),15,c);d.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);d.setTypeface(Typeface.DEFAULT,Typeface.BOLD);row.addView(d,new LinearLayout.LayoutParams(dp(390),dp(58)));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(70));rp.setMargins(0,0,0,dp(2));list.addView(row,rp);}
  }
  void addProductDialog(){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(22),0,dp(22),0);EditText name=new EditText(this);name.setHint("Nombre del producto");box.addView(name);EditText icon=new EditText(this);icon.setHint("Icono opcional, por ejemplo 🍗");box.addView(icon);new AlertDialog.Builder(this).setTitle("Añadir producto").setView(box).setNegativeButton("Cancelar",null).setPositiveButton("Guardar",(d,w)->{String n=name.getText().toString().trim(),i=icon.getText().toString().trim();if(!n.isEmpty()){db.addProduct(n,i.isEmpty()?"🍽️":i);showProducts();}}).show();}

  static class Product{long id;String name,icon;Product(long i,String n,String e){id=i;name=n;icon=e;}}
  static class Record{long id;String name,icon;long time;Record(long x,String n,String i,long t){id=x;name=n;icon=i;time=t;}}
  public static class Db extends SQLiteOpenHelper{
    Db(Context c){super(c,"elaboraciones.db",null,1);}
    public void onCreate(SQLiteDatabase d){d.execSQL("CREATE TABLE products(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,icon TEXT NOT NULL)");d.execSQL("CREATE TABLE records(id INTEGER PRIMARY KEY AUTOINCREMENT,product_id INTEGER NOT NULL,created_at INTEGER NOT NULL)");String[][] p={{"Chuletas de pavo al ajillo","🍗"},{"Pavo adobado","🥩"},{"Chispas de pollo","🍗"},{"Solomillos empanados","🥖"},{"Salchichas","🌭"},{"San Jacobos","🧀"},{"Villarroy","🍽️"},{"Buffalo Wings","🔥"},{"Alitas miel y mostaza","🍯"},{"Albóndigas","🧆"},{"Croquetas","🥖"},{"Donuts de pollo","🍩"},{"Burger de quesitos","🍔"},{"Burger de espinacas","🥬"},{"Burger natural","🍔"},{"Preparado de kebab","🥙"}};for(String[] x:p){ContentValues v=new ContentValues();v.put("name",x[0]);v.put("icon",x[1]);d.insert("products",null,v);}}
    public void onUpgrade(SQLiteDatabase d,int o,int n){}
    void addProduct(String n,String i){ContentValues v=new ContentValues();v.put("name",n);v.put("icon",i);getWritableDatabase().insert("products",null,v);}
    long addRecord(long p,long t){ContentValues v=new ContentValues();v.put("product_id",p);v.put("created_at",t);return getWritableDatabase().insert("records",null,v);}
    void deleteRecord(long id){getWritableDatabase().delete("records","id=?",new String[]{String.valueOf(id)});}
    List<Product> products(){List<Product>a=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT id,name,icon FROM products ORDER BY id",null)){while(c.moveToNext())a.add(new Product(c.getLong(0),c.getString(1),c.getString(2)));}return a;}
    Long last(long id){try(Cursor c=getReadableDatabase().rawQuery("SELECT MAX(created_at) FROM records WHERE product_id=?",new String[]{String.valueOf(id)})){if(c.moveToFirst()&&!c.isNull(0))return c.getLong(0);}return null;}
    List<Record> records(long id){List<Record>a=new ArrayList<>();String sql="SELECT r.id,p.name,p.icon,r.created_at FROM records r JOIN products p ON p.id=r.product_id"+(id==0?"":" WHERE p.id=?")+" ORDER BY r.created_at DESC";String[] args=id==0?null:new String[]{String.valueOf(id)};try(Cursor c=getReadableDatabase().rawQuery(sql,args)){while(c.moveToNext())a.add(new Record(c.getLong(0),c.getString(1),c.getString(2),c.getLong(3)));}return a;}
  }

  static void scheduleExpiry(Context c,long id,String name,long created){long when=created+5*DAY;if(when<=System.currentTimeMillis())return;Intent i=new Intent(c,ExpiryReceiver.class);i.putExtra("name",name);i.putExtra("id",id);PendingIntent pi=PendingIntent.getBroadcast(c,(int)id,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);((AlarmManager)c.getSystemService(ALARM_SERVICE)).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi);}
  static void cancelExpiry(Context c,long id){Intent i=new Intent(c,ExpiryReceiver.class);PendingIntent pi=PendingIntent.getBroadcast(c,(int)id,i,PendingIntent.FLAG_NO_CREATE|PendingIntent.FLAG_IMMUTABLE);if(pi!=null){((AlarmManager)c.getSystemService(ALARM_SERVICE)).cancel(pi);pi.cancel();}}
  public static class ExpiryReceiver extends BroadcastReceiver{public void onReceive(Context c,Intent i){String name=i.getStringExtra("name");long id=i.getLongExtra("id",1);Intent open=new Intent(c,MainActivity.class);PendingIntent tap=PendingIntent.getActivity(c,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(c,CHANNEL):new Notification.Builder(c);b.setSmallIcon(R.drawable.app_icon).setContentTitle("Producto a punto de caducar").setContentText(name+" caduca mañana").setStyle(new Notification.BigTextStyle().bigText(name+" caduca mañana. Revisa las elaboraciones en la tablet.")).setAutoCancel(true).setContentIntent(tap);((NotificationManager)c.getSystemService(NOTIFICATION_SERVICE)).notify((int)id,b.build());}}
  public static class BootReceiver extends BroadcastReceiver{public void onReceive(Context c,Intent i){Db d=new Db(c);for(Record r:d.records(0))scheduleExpiry(c,r.id,r.name,r.time);}}
}

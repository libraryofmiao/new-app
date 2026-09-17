package `in`.miaolibrary.app

import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private val gateway = "https://api.miaolibrary.in"
    private val bg = Color.rgb(247,243,236)
    private val navy = Color.rgb(28,45,63)
    private val blue = Color.rgb(45,83,111)
    private lateinit var content: FrameLayout
    private fun dp(v:Int)= (v*resources.displayMetrics.density).toInt()
    private fun tv(s:String,size:Float=16f,color:Int=navy)=TextView(this).apply{text=s;textSize=size;setTextColor(color)}
    private fun prefs()=getSharedPreferences("session",MODE_PRIVATE)
    private fun token()=prefs().getString("access_token",null)
    private fun username()=prefs().getString("username","Patron")?:"Patron"
    private fun image(size:Int=92)=ImageView(this).apply{setImageResource(R.drawable.logo);adjustViewBounds=true;scaleType=ImageView.ScaleType.CENTER_INSIDE;layoutParams=LinearLayout.LayoutParams(-1,dp(size))}

    override fun onCreate(state:Bundle?){super.onCreate(state);window.statusBarColor=bg;window.navigationBarColor=bg;window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;if(token().isNullOrBlank())showLogin()else showDashboard("Home")}

    private fun showLogin(){
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;setPadding(dp(28),dp(20),dp(28),dp(28));setBackgroundColor(bg)}
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_HORIZONTAL}
        box.addView(image(130));box.addView(tv("Miao Library",30f).apply{gravity=Gravity.CENTER;setTypeface(typeface,1)},LinearLayout.LayoutParams(-1,dp(55)));box.addView(tv("Sign in to access your library account",15f,Color.DKGRAY).apply{gravity=Gravity.CENTER},LinearLayout.LayoutParams(-1,dp(45)))
        val user=EditText(this).apply{hint="Library username";setSingleLine();inputType=InputType.TYPE_CLASS_TEXT};val pass=EditText(this).apply{hint="Password";setSingleLine();inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD};box.addView(user,LinearLayout.LayoutParams(-1,dp(56)).apply{setMargins(0,dp(10),0,0)});box.addView(pass,LinearLayout.LayoutParams(-1,dp(56)))
        val b=Button(this).apply{text="Sign in";isAllCaps=false};box.addView(b,LinearLayout.LayoutParams(-1,dp(54)).apply{setMargins(0,dp(20),0,0)});b.setOnClickListener{val u=user.text.toString().trim();val p=pass.text.toString();if(u.isBlank()||p.isBlank()){toast("Enter your username and password.");return@setOnClickListener};b.isEnabled=false;b.text="Signing in…";request("/login","POST",JSONObject().put("username",u).put("password",p),null){ok,body->runOnUiThread{b.isEnabled=true;b.text="Sign in";if(!ok){toast("Login failed. Please check your credentials.");return@runOnUiThread};try{val j=JSONObject(body);val t=j.optString("access_token").ifBlank{j.optString("token")};if(t.isBlank())throw Exception();prefs().edit().putString("access_token",t).putString("username",u).apply();showDashboard("Home")}catch(_:Exception){toast("Invalid gateway response.")}}}}
        root.addView(box,LinearLayout.LayoutParams(-1,-2));setContentView(root)
    }

    private fun showDashboard(section:String){
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(bg);fitsSystemWindows=true}
        val top=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(18),dp(12),dp(18),dp(8))};top.addView(ImageView(this).apply{setImageResource(R.drawable.logo);scaleType=ImageView.ScaleType.CENTER_INSIDE},LinearLayout.LayoutParams(dp(64),dp(64)));val heading=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(12),0,0,0)};heading.addView(tv("Miao Library",25f).apply{setTypeface(typeface,1)});heading.addView(tv("Welcome back, ${username()}",14f,Color.DKGRAY));top.addView(heading,LinearLayout.LayoutParams(0,-2,1f));root.addView(top)
        content=FrameLayout(this);root.addView(content,LinearLayout.LayoutParams(-1,0,1f))
        val nav=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER;setBackgroundColor(Color.WHITE);elevation=dp(8).toFloat();setPadding(0,0,0,dp(4));setOnApplyWindowInsetsListener{v,i->v.setPadding(0,0,0,i.systemWindowInsetBottom);i}}
        listOf("Home","Catalogue","My Books","Account").forEach{name->val item=TextView(this).apply{text=name;textSize=12f;gravity=Gravity.CENTER;setTextColor(blue);isClickable=true;isFocusable=true;setOnClickListener{showDashboard(name)}};nav.addView(item,LinearLayout.LayoutParams(0,dp(62),1f))};root.addView(nav,LinearLayout.LayoutParams(-1,dp(62)));setContentView(root);root.requestApplyInsets();showSection(section)
    }

    private fun showSection(section:String){content.removeAllViews();val scroll=ScrollView(this).apply{isFillViewport=true};val body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(22),dp(8),dp(22),dp(36))};body.addView(tv(section,22f).apply{setTypeface(typeface,1)});when(section){"Home"->loadHome(body);"Catalogue"->showCatalogue(body);"My Books"->loadBooks(body);"Account"->showAccount(body)};scroll.addView(body);content.addView(scroll,FrameLayout.LayoutParams(-1,-1))}

    private fun loadHome(body:LinearLayout){body.addView(tv("Library announcements and updates",14f,Color.DKGRAY));val loading=tv("Loading announcements…");loading.setPadding(dp(18),dp(18),dp(18),dp(18));loading.setBackgroundColor(Color.WHITE);body.addView(loading,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,dp(16),0,0)});request("/cms/content","GET",null,token()){ok,response->runOnUiThread{if(!ok){loading.text="Announcements are temporarily unavailable.";return@runOnUiThread};try{val arr=arrayFrom(response,"items","content","announcements");body.removeView(loading);if(arr.length()==0)body.addView(tv("No current announcements."))else for(i in 0 until arr.length()){val x=arr.optJSONObject(i)?:continue;val title=first(x,"title","name").ifBlank{"Announcement"};val text=first(x,"body","description","html","content");val v=tv("$title\n$text",15f);v.setPadding(dp(18),dp(16),dp(18),dp(16));v.setBackgroundColor(Color.WHITE);body.addView(v,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,0,0,dp(12))})}}catch(_:Exception){loading.text="Unable to display announcements."}}}}

    private fun showCatalogue(body:LinearLayout){val input=EditText(this).apply{hint="Search books";setSingleLine()};body.addView(input,LinearLayout.LayoutParams(-1,dp(54)).apply{setMargins(0,dp(14),0,dp(10))});val b=Button(this).apply{text="Search catalogue";isAllCaps=false};body.addView(b,LinearLayout.LayoutParams(-1,dp(50)));val results=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};body.addView(results,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,dp(16),0,0)});b.setOnClickListener{searchCatalogue(input.text.toString().trim(),results,b)};searchCatalogue("",results,b)}

    private fun searchCatalogue(q:String,results:LinearLayout,b:Button){b.isEnabled=false;results.removeAllViews();results.addView(tv("Loading catalogue…"));request("/catalogue/search?q="+java.net.URLEncoder.encode(q,"UTF-8"),"GET",null,token()){ok,response->runOnUiThread{b.isEnabled=true;results.removeAllViews();if(!ok){results.addView(tv("Catalogue is temporarily unavailable."));return@runOnUiThread};try{val arr=arrayFrom(response,"items","results","records","books");if(arr.length()==0)results.addView(tv("No catalogue records found."))else for(i in 0 until arr.length()){val x=arr.optJSONObject(i)?:continue;val v=tv(first(x,"title","name").ifBlank{"Untitled"}+"\n"+first(x,"author","authors","creator")+"\n"+first(x,"library","location"),15f);v.setPadding(dp(16),dp(14),dp(16),dp(14));v.setBackgroundColor(Color.WHITE);v.isClickable=true;v.setOnClickListener{openBook(x)};results.addView(v,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,0,0,dp(10))})}}catch(_:Exception){results.addView(tv("Unable to read catalogue response."))}}}}

    private fun openBook(x:JSONObject){val id=first(x,"biblionumber","biblio_id","id");if(id.isBlank()){showBookDetails(x);return};request("/book-details/$id","GET",null,token()){ok,response->runOnUiThread{if(ok)try{val j=JSONObject(response);showBookDetails(j.optJSONObject("book")?:j.optJSONObject("record")?:j)}catch(_:Exception){showBookDetails(x)}else showBookDetails(x)}}}
    private fun showBookDetails(x:JSONObject){showDashboard("Catalogue");content.removeAllViews();val s=ScrollView(this);val b=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(22),dp(12),dp(22),dp(36))};b.addView(tv("Book details",22f).apply{setTypeface(typeface,1)});val keys=arrayOf("title","subtitle","author","authors","publisher","publication","publication_year","year","isbn","call_number","library","location","availability","status","barcode","holding_count","copy_count","holdings","item_type","notes","biblionumber");for(k in keys){val v=first(x,k);if(v.isNotBlank()){val row=tv("${k.replace('_',' ').replaceFirstChar{it.uppercase()}}\n$v",15f);row.setPadding(dp(16),dp(12),dp(16),dp(12));row.setBackgroundColor(Color.WHITE);b.addView(row,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,0,0,dp(8))})}};s.addView(b);content.addView(s,FrameLayout.LayoutParams(-1,-1))}

    private fun loadBooks(body:LinearLayout){val history=Button(this).apply{text="Issue History / Previous Issues";isAllCaps=false};body.addView(history,LinearLayout.LayoutParams(-1,dp(50)).apply{setMargins(0,dp(14),0,dp(12))});val list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};body.addView(list);history.setOnClickListener{loadHistoryInto(list)};request("/my-books","GET",null,token()){ok,response->runOnUiThread{if(!ok){list.addView(tv("Unable to load your books."));return@runOnUiThread};try{val arr=arrayFrom(response,"books","items","issues");if(arr.length()==0)list.addView(tv("You have no currently issued books."))else renderRecords(arr,list,true)}catch(_:Exception){list.addView(tv("Unable to read your books."))}}}}
    private fun loadHistoryInto(list:LinearLayout){list.removeAllViews();list.addView(tv("Loading issue history…"));request("/issue-history","GET",null,token()){ok,response->runOnUiThread{list.removeAllViews();if(!ok){list.addView(tv("Unable to load issue history."));return@runOnUiThread};try{val arr=arrayFrom(response,"items","history","issues","books");if(arr.length()==0)list.addView(tv("No previous issues found."))else renderRecords(arr,list,false)}catch(_:Exception){list.addView(tv("Unable to read issue history."))}}}}
    private fun renderRecords(arr:JSONArray,list:LinearLayout,current:Boolean){for(i in 0 until arr.length()){val x=arr.optJSONObject(i)?:continue;val text=first(x,"title","name").ifBlank{"Untitled"}+"\n"+first(x,"author","authors")+"\n"+(if(current)"Due: " else "Issued: ")+first(x,"date_due","due_date","checkout_date","issued_date","date_issued").ifBlank{"Not available"})+"\n"+first(x,"returned_date","checkin_date","date_returned","library","barcode");val v=tv(text,15f);v.setPadding(dp(16),dp(16),dp(16),dp(16));v.setBackgroundColor(Color.WHITE);list.addView(v,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,0,0,dp(10))})}}

    private fun showAccount(body:LinearLayout){body.addView(tv("Signed in as ${username()}",16f),LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,dp(14),0,dp(20))});val h=Button(this).apply{text="Open Issue History";isAllCaps=false};body.addView(h,LinearLayout.LayoutParams(-1,dp(50)));h.setOnClickListener{showDashboard("My Books")};val out=Button(this).apply{text="Log out";isAllCaps=false};body.addView(out,LinearLayout.LayoutParams(-1,dp(50)).apply{setMargins(0,dp(12),0,0)});out.setOnClickListener{prefs().edit().clear().apply();showLogin()}}

    private fun first(x:JSONObject,vararg keys:String):String{for(k in keys){val v=x.opt(k);if(v!=null&&!v.toString().equals("null",true)&&v.toString().isNotBlank())return if(v is JSONArray)v.join(", ")else v.toString()}return ""}
    private fun arrayFrom(raw:String,vararg keys:String):JSONArray{val t=raw.trim();if(t.startsWith("["))return JSONArray(t);val j=JSONObject(t);for(k in keys){val a=j.optJSONArray(k);if(a!=null)return a;val o=j.optJSONObject(k);if(o!=null){val nested=arrayFrom(o.toString(),*keys);if(nested.length()>0)return nested}};return JSONArray()}
    private fun request(path:String,method:String,payload:JSONObject?,auth:String?,cb:(Boolean,String)->Unit){thread{var c:HttpURLConnection?=null;try{c=(URL(gateway+path).openConnection() as HttpURLConnection).apply{requestMethod=method;connectTimeout=15000;readTimeout=20000;setRequestProperty("Accept","application/json");if(auth!=null)setRequestProperty("Authorization","Bearer $auth");if(payload!=null){doOutput=true;setRequestProperty("Content-Type","application/json")}};if(payload!=null)c.outputStream.use{it.write(payload.toString().toByteArray())};val code=c.responseCode;val stream=if(code in 200..299)c.inputStream else c.errorStream;cb(code in 200..299,stream?.bufferedReader()?.use{it.readText()}.orEmpty())}catch(e:Exception){cb(false,e.message.orEmpty())}finally{c?.disconnect()}}}
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_LONG).show()
}

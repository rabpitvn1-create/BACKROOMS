package android.content;
import android.content.res.AssetManager;
public class Context {
 public static final int MODE_PRIVATE=0;
 private final AssetManager assets=new AssetManager();
 public Context getApplicationContext(){return this;}
 public AssetManager getAssets(){return assets;}
 public SharedPreferences getSharedPreferences(String name,int mode){return new SharedPreferences(){public boolean contains(String key){return false;}public boolean getBoolean(String key,boolean value){return value;}};}
}

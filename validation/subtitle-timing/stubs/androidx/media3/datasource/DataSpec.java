package androidx.media3.datasource;
public class DataSpec {
 public static final int HTTP_METHOD_GET=1;
 public android.net.Uri uri; public long position=0,length=-1; public int httpMethod=1;
 public java.util.Map<String,String> httpRequestHeaders=new java.util.HashMap<>();
 public DataSpec(android.net.Uri u){uri=u;}
 public Builder buildUpon(){return new Builder(this);}
 public static class Builder{
 private DataSpec s; public Builder(DataSpec s){this.s=s;}
 public Builder setHttpRequestHeaders(java.util.Map<String,String> h){s.httpRequestHeaders=h;return this;}
 public DataSpec build(){return s;}
 }
}

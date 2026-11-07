package androidx.media3.datasource;
public class ByteArrayDataSource implements DataSource{
 private byte[] b; private int p; private android.net.Uri uri;
 public ByteArrayDataSource(byte[] b){this.b=b;}
 public void addTransferListener(TransferListener l){}
 public long open(DataSpec s){uri=s.uri;p=0;return b.length;}
 public int read(byte[] out,int o,int l){if(p>=b.length)return -1;int n=Math.min(l,b.length-p);System.arraycopy(b,p,out,o,n);p+=n;return n;}
 public android.net.Uri getUri(){return uri;}
 public java.util.Map<String,java.util.List<String>> getResponseHeaders(){return java.util.Collections.emptyMap();}
 public void close(){}
}

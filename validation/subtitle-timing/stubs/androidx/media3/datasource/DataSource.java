package androidx.media3.datasource;
public interface DataSource {
 void addTransferListener(TransferListener l);
 long open(DataSpec s);
 int read(byte[] b,int o,int l);
 android.net.Uri getUri();
 java.util.Map<String,java.util.List<String>> getResponseHeaders();
 void close();
 interface Factory { DataSource createDataSource(); }
}

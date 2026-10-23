package dz.electrogas;

import android.content.*;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;
import java.util.List;

/** Narrow URI surface: camera can write only capture files; exports are read-only. */
public final class InvoiceProvider extends ContentProvider {
    public static final String AUTHORITY="dz.electrogas.documents";
    @Override public boolean onCreate(){return true;}
    public static Uri capture(Context c,String name){return new Uri.Builder().scheme("content").authority(AUTHORITY).appendPath("capture").appendPath(name).build();}
    public static Uri exported(String name){return new Uri.Builder().scheme("content").authority(AUTHORITY).appendPath("exports").appendPath(name).build();}
    private File resolve(Uri uri) throws FileNotFoundException {
        List<String> p=uri.getPathSegments();if(p.size()!=2||!InvoiceStore.safeName(p.get(1)))throw new FileNotFoundException("URI invalide");
        File dir;if(p.get(0).equals("capture")){if(!p.get(1).endsWith(".jpg"))throw new FileNotFoundException();dir=new File(getContext().getCacheDir(),"invoice-capture");}
        else if(p.get(0).equals("exports"))dir=new File(new InvoiceStore(getContext()).cache,"exports");else throw new FileNotFoundException();
        dir.mkdirs();return new File(dir,p.get(1));
    }
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode) throws FileNotFoundException {
        File f=resolve(uri);if(!mode.equals("r")&&!uri.getPathSegments().get(0).equals("capture"))throw new FileNotFoundException("Lecture seule");
        int flags;switch(mode){case "r":flags=ParcelFileDescriptor.MODE_READ_ONLY;break;case "w":case "wt":flags=ParcelFileDescriptor.MODE_WRITE_ONLY|ParcelFileDescriptor.MODE_CREATE|ParcelFileDescriptor.MODE_TRUNCATE;break;
            case "wa":flags=ParcelFileDescriptor.MODE_WRITE_ONLY|ParcelFileDescriptor.MODE_CREATE|ParcelFileDescriptor.MODE_APPEND;break;case "rw":flags=ParcelFileDescriptor.MODE_READ_WRITE|ParcelFileDescriptor.MODE_CREATE;break;
            case "rwt":flags=ParcelFileDescriptor.MODE_READ_WRITE|ParcelFileDescriptor.MODE_CREATE|ParcelFileDescriptor.MODE_TRUNCATE;break;default:throw new FileNotFoundException("Mode invalide");}
        return ParcelFileDescriptor.open(f,flags);
    }
    @Override public String getType(Uri uri){return uri.getLastPathSegment()!=null&&uri.getLastPathSegment().endsWith(".pdf")?"application/pdf":"image/jpeg";}
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort){
        try{File f=resolve(uri);String[] cols=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;MatrixCursor c=new MatrixCursor(cols);Object[] row=new Object[cols.length];for(int i=0;i<cols.length;i++)row[i]=cols[i].equals(OpenableColumns.DISPLAY_NAME)?(f.getName().endsWith(".pdf")?"Facture-ElectroGas.pdf":"Facture.jpg"):cols[i].equals(OpenableColumns.SIZE)?f.length():null;c.addRow(row);return c;}
        catch(FileNotFoundException e){return null;}
    }
    @Override public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
    @Override public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
    @Override public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
}

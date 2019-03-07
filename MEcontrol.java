import java.io.*;
import java.util.*;
// Data structure to store neighbor information 
public class MEcontrol
{
    public int ME;
    public int N = 5;
    public String filename;
    public int our_sn;
    public int high_sn;
    public boolean using;
    public boolean waiting;
    public Boolean[] A = new Boolean[5];
    public Boolean[] reply_deferred = new Boolean[5];
    MEcontrol(int ME,String filename)
    {
        this.our_sn = 0;
        this.high_sn= 0;
        this.using = false;
        this.waiting= false;
        this.ME = ME;
        this.filename = filename;
        Arrays.fill(A, Boolean.FALSE);
        Arrays.fill(reply_deferred, Boolean.FALSE);
        A[ME] = true;
    }
}

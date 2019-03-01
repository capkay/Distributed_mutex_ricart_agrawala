import java.io.*;
import java.util.*;
// Data structure to store neighbor information 
public class MEcontrol
{
    public int ME;
    public int N = 5;
    public int our_sn;
    public int high_sn;
    public boolean using;
    public boolean waiting;
    public Boolean[] A = new Boolean[5];
    public Boolean[] reply_deferred = new Boolean[5];
    MEcontrol(int ME)
    {
        this.our_sn = 0;
        this.high_sn= 0;
        this.using = false;
        this.waiting= false;
        this.ME = ME;
        Arrays.fill(A, Boolean.FALSE);
        Arrays.fill(reply_deferred, Boolean.FALSE);
        A[ME] = true;
    }
}

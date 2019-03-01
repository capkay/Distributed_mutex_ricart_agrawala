import java.io.*;
import java.util.*;
// Data structure to store neighbor information 
public class RequestS
{
    public int local_clk;
    public int reply_count;
    public int replies_needed;
    public boolean in_crit_section;
    public boolean current_request;
    public Boolean[] replies_pending = new Boolean[5];
    public int request_ts;
    public Boolean[] optimized_reply = new Boolean[5];
    RequestS()
    {
        this.local_clk = 0;
        this.in_crit_section = false;
        this.current_request = false;
        this.request_ts = 0;
        this.reply_count= 0;
        Arrays.fill(optimized_reply, Boolean.FALSE);
        Arrays.fill(replies_pending, Boolean.FALSE);
    }
}

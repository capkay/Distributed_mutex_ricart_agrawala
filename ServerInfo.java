import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;
import java.util.Set;
// Data structure to store neighbor information 
public class ServerInfo
{
    public HashMap<Integer, IPData> hmap = new HashMap<Integer, IPData>();
    ServerInfo()
    {
        IPData s0 = new IPData("localhost","9100");
        IPData s1 = new IPData("localhost","9101");
        IPData s2 = new IPData("localhost","9102");
        this.hmap.put(0,s0);
        this.hmap.put(1,s1);
        this.hmap.put(2,s2);
    }
}

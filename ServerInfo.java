import java.util.HashMap;
// Data structure initialized to have server endpoint information
public class ServerInfo
{
    // define hashmap to hold server Info <Id, Endpoint Info>
    public HashMap<Integer, IPData> hmap = new HashMap<Integer, IPData>();
    ServerInfo()
    {
        // create and populate data
        IPData s0 = new IPData("dc06.utdallas.edu","9100");
        IPData s1 = new IPData("dc07.utdallas.edu","9101");
        IPData s2 = new IPData("dc08.utdallas.edu","9102");
        this.hmap.put(0,s0);
        this.hmap.put(1,s1);
        this.hmap.put(2,s2);
    }
}

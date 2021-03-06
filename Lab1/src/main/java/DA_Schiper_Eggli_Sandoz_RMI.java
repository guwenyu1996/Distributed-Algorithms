import java.rmi.Remote;
import java.rmi.RemoteException;

public interface DA_Schiper_Eggli_Sandoz_RMI extends Remote  {

    /**
     * Send a message to target process.
     * @param node
     * @param message
     * @throws RemoteException
     */
    void send(int node, Message message) throws RemoteException;

    /**
     * Receive a process from a remote process. Processes should be delivered in a casual order.
     * @param message message to receive
     */
    void receive(Message message) throws RemoteException;

    void test() throws RemoteException;

    void clear() throws RemoteException;
}

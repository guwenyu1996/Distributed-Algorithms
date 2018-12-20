import org.apache.log4j.Logger;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;

/**
 * Map of data structure from pseudo code to it
 * j -> src
 * SE(x) -> SE.get(x),getSE()
 * w(x) -> SE.get(x).getWeight()
 * if you want to invoke a function on node k, SE.get(k).getNode().foo()
 */

public class MST extends UnicastRemoteObject implements MST_RMI, Runnable{

    private final int INF = Integer.MAX_VALUE;
    private final int NIL = -1;

    /**
     * The total number of process in the system
     */
    private int processNum;

    /**
     * The index of current process
     */
    private int index;

    /**
     * level of the current fragment it is part of
     */
    private int LN;
    /**
     * name of the current fragment it is part of
     */
    private int FN;

    /**
     * state of the node (find/found)
     */
    private State_node SN;
    /**
     * index of node on the other side of the edge towards core
     */
    private int in_branch;
    /**
     * index of node on the other side of the test edge
     */
    private int test_edge;
    /**
     * index of the node on the other side of the best edge, best edge is the local candidate of the MOE
     */
    private int best_edge;
    /**
     * weight tof the current candidate MOE
     */
    private int best_weight;
    /**
     * number of report messages expected
     */
    private int find_count;

    private boolean halt;


    /**
     *  instead of storing the information of the edges, i prefer storing the
     *  information of the nodes on the other side of the edge
     *  the SE(j) in the ppt is stored in this map
     */
    private Map<Integer, NeighbourNode> SE;

    private Queue<Message> queue;


    final static Logger logger = Logger.getLogger(MST.class);

    public MST(int processNum, int index)throws RemoteException {
        this.processNum = processNum;
        this.index = index;
        SN = State_node.Sleeping;
        SE = new HashMap<Integer, NeighbourNode>();
        LN = 0;
        FN = index;
        queue = new LinkedList<Message>();
        halt = false;
        find_count = 0;
        test_edge = NIL;
        best_weight = INF;
    }


    /**
     * /unction to inform this node the index of neighbouring node, and the weight of the edge to
     *  this node, thus we can construct a graph, should ONLY be invoked by the test function
     * @param SE
     */
    public void construct_key( Map<Integer, NeighbourNode> SE)  throws RemoteException{
        this.SE = SE;
    }

    /**
     * If receives an initial message, propagate new information of tree to nodes in tree and
     * start searching for MOE to nodes are likely in tree.
     * @param src index of the source
     * @param level level of the sender
     * @param fragment_name fragment name of the sender
     * @param s state of the sender
     */
    public void receive_initiate(int src, int level, int fragment_name, State_node s) throws RemoteException{

        logger.info("< " + LN + ", " + FN + ", " + SN + "> " + "find count: " + find_count + "test edge: " + test_edge +
                "P" + src + " Initiate Msg: level = " + level + " frag = " + fragment_name + " state = " + s);
        LN = level;
        FN = fragment_name;
        SN = s;
        in_branch = src;
        best_edge = NIL;
        best_weight = INF;

        for (Map.Entry<Integer, NeighbourNode> iter : SE.entrySet()){
            NeighbourNode neighbour = iter.getValue();

            if(neighbour.getSE() == State_edge.In_MST && neighbour.getIndex()!= src){
                neighbour.getNode().receive_initiate(index, LN, FN, SN);
                if(neighbour.getNode().getSN() == State_node.Find)
                    find_count ++;
            }
        }

        // searching for MOE
        if(SN == State_node.Find)
            test();
    }

    /**
     * Find a MOE.
     * @throws RemoteException
     */
    public void test() throws RemoteException{

        // search for MOE: a neighbour which has a minimal weight and is not examined
        int minNeigh = -1, min = Integer.MAX_VALUE;
        for (Map.Entry<Integer, NeighbourNode> iter : SE.entrySet()){

            NeighbourNode neighbour = iter.getValue();
            if(neighbour.getSE() == State_edge.P_in_MST && neighbour.getWeight() < min){
                min = neighbour.getWeight();
                minNeigh = neighbour.getIndex();
            }
        }

        // if such neighbour node is founded, send test message
        // if no, means all neighbours have been tested, report the best edge right now
        if(minNeigh != -1){
            test_edge = minNeigh;
            SE.get(minNeigh).getNode().receive_test(index, LN, FN);
            logger.info("< " + LN + ", " + FN + ", " + SN + "> " + "find count: " + find_count + "test edge: " + test_edge +
                    "Test: Send Test Msg to P" + minNeigh);
        }
        else{
            logger.info("< " + LN + ", " + FN + ", " + SN + "> " + "find count: " + find_count + "test edge: " + test_edge +
                    "Test: no MOE");
            test_edge = NIL;
            report();
        }
    }

    /**
     *
     * @param src
     * @param level
     * @param fragment_name
     * @throws RemoteException
     */
    public void receive_test(int src, int level, int fragment_name) throws RemoteException{
        logger.info("< " + LN + ", " + FN + ", " + SN + "> " + "find count: " + find_count + "test edge: " + test_edge +
                "Receive P" + src + " Test Msg: level = " + level + " frag = " + fragment_name);

        if(SN == State_node.Sleeping){
            wakeup();
            return;
        }

        handleQueue();

        if(level > LN){
            Message msg = new Message(MessageType.TEST, src);
            msg.setLevel(level);
            msg.setFragment(fragment_name);
            queue.add(msg);

            logger.info("< " + LN + ", " + FN + ", " + SN + "> " + "find count: " + find_count + "test edge: " + test_edge +
                    "P" + src + "find count: " + find_count + "test edge: " + test_edge + " Test Msg is postponed.");
        }else{ // absorb
            // absorb subtree which is not in same fragment
            if(fragment_name != FN){
                logger.info("< " + LN + ", " + FN + ", " + SN + "> " + "find count: " + find_count + "test edge: " + test_edge +
                        "P" + src + "find count: " + find_count + "test edge: " + test_edge + " Test Msg is accepted.");
                SE.get(src).getNode().receive_accept(index);
            }
                // Reject if neighbour node is in same fragment
            else{
                if(fragment_name != FN) {
                    SE.get(src).getNode().receive_accept(index);
                }else{
                    logger.info("< " + LN + ", " + FN + ", " + SN + "> " + "find count: " + find_count + "test edge: " + test_edge +
                            "P" + src + "find count: " + find_count + "test edge: " + test_edge + " Test Msg is rejected.");

                    if (SE.get(src).getSE() == State_edge.P_in_MST)
                        SE.get(src).setSE(State_edge.Not_in_MST);

                    if (test_edge != src)
                        SE.get(src).getNode().receive_reject(index);
                    else
                        test();
                }
            }
        }
    }

    /**
     * If receive an accept, absorbs a subtree.
     */
    public void receive_accept(int src) throws RemoteException{
        logger.info("< " + LN + ", " + FN + ", " + SN + "> " + "find count: " + find_count + "test edge: " + test_edge +
                "Receive P" + src + "Accept Msg");
        test_edge = NIL;
        int propose = SE.get(src).getWeight();
        if( propose < best_weight){
            best_edge = src;
            best_weight = propose;
            logger.info("< " + LN + ", " + FN + ", " + SN + "> " + "find count: " + find_count + "test edge: " + test_edge +
                    "bestW = " + best_weight + "bestE = " + best_edge);
        }
        report();
    }

    public void receive_reject(int src) throws RemoteException{
        logger.info("< " + LN + ", " + FN + ", " + SN + "> " + "find count: " + find_count + "test edge: " + test_edge +
                "Receive P" + src + "Reject Msg");
        if(SE.get(src).getSE() == State_edge.P_in_MST)
            SE.get(src).setSE(State_edge.Not_in_MST);

        test();
    }

    /**
     * If a node receives report from children, compare the best weight
     * from the children and from itself, and report to its parents.
     *
     * If a node receives report from core node, determine to merge two
     * subtrees or end constructing.
     * @param src
     * @param weight
     * @throws RemoteException
     */
    public void receive_report (int src, int weight) throws RemoteException{
        logger.info("< " + LN + ", " + FN + ", " + SN + "> " + "find count: " + find_count + "test edge: " + test_edge +
                "Receive P" + src + "Report Msg with weight = " + weight + "bestW = " + best_weight);
        handleQueue();

        if(src != this.in_branch){
            // receive report from own subtree
            find_count --;
            if(weight < best_weight){
                best_weight = weight;
                best_edge = src;
            }
            report();
        }else if(SN == State_node.Found){
            // receive report from the other side of core edge
            if(weight > this.best_weight){
                change_root();
            }else if(weight == INF && best_weight == INF){
                halt();
            }
        }
    }

    /**
     * Receive a change root message.
     * @throws RemoteException
     */
    public void receive_change_root() throws RemoteException{
        logger.info("< " + LN + ", " + FN + ", " + SN + "> " + "find count: " + find_count + "test edge: " + test_edge +
                "Receive Change Root Msg");
        change_root();
    }

    private void change_root() throws RemoteException{
        if(this.SE.get(this.best_edge).getSE() == State_edge.In_MST){
            // propagate change root message if it does know the real best edge
            this.SE.get(this.best_edge).getNode().receive_change_root();
        }else{
            // try to merge two subtrees
            this.SE.get(this.best_edge).getNode().receive_connect(this.index,this.LN);
            this.SE.get(best_edge).setSE(State_edge.In_MST);
        }
    }

    /**
     * Receive a connect message.
     * If levels of two nodes are different, absorb one with lower level.
     * If same, merge two nodes.
     * @param src
     * @param level
     * @throws RemoteException
     */
    public void receive_connect(int src, int level) throws RemoteException{
        logger.info("< " + LN + ", " + FN + ", " + SN + "> " + "find count: " + find_count + "test edge: " + test_edge +
                "Receive P" + src + "Connect Msg with level = " + level);

        if(this.SN == State_node.Sleeping){
            wakeup();
        }

        handleQueue();

        if(level < this.LN){
            SE.get(src).setSE(State_edge.In_MST);
            SE.get(src).getNode().receive_initiate(this.index, this.LN, this.FN, this.SN);
            if(this.SN == State_node.Find){
                this.find_count ++;
            }

            if(src == test_edge)
                test();

        }else{
            // merge two subtrees
            if(SE.get(src).getSE() == State_edge.P_in_MST){
                Message msg = new Message(MessageType.CONNECT, index);
                msg.setLevel(LN);
                queue.add(msg);
            }else{
                //use the weight of the common edge as the fragment name
                LN ++;
                FN = this.SE.get(src).getWeight();
                in_branch = src;
                SN = State_node.Find;
                SE.get(src).getNode().receive_initiate(this.index, this.LN, FN, State_node.Find);
                test();
            }
        }
    }


    public void receive_print(int src) throws RemoteException{

        for (Map.Entry<Integer, NeighbourNode> iter : SE.entrySet()) {
            NeighbourNode neighbour = iter.getValue();
            if(neighbour.getSE() == State_edge.In_MST) {
                logger.info("P" + index + " ------- P" + iter.getKey() + " weight = "
                        + iter.getValue().getWeight());

                if(src != neighbour.getIndex())
                    neighbour.getNode().receive_print(index);
            }
        }
    }


    /**
     * Function for thread
     */
    public void run() {
        logger.info("Run process " + index);
    }

    /**
     * Report the best edge to the parent node.
     */
    private void report() throws RemoteException{
        if(this.find_count == 0 && this.test_edge == NIL){
            logger.info("< " + LN + ", " + FN + ", " + SN + "> " + "find count: " + find_count + "test edge: " + test_edge +
                    "Send Report to P" + in_branch + " bestW = " + best_weight);
            this.SN =  State_node.Found;
            this.SE.get(this.in_branch).getNode().receive_report(this.index,this.best_weight);

        }
    }

    /**
     * Activate a node by send a connect message to MOE.
     */
    private void wakeup() throws RemoteException{
        logger.info("Wake up");

        // searching for neighbour nodes with minimal weight
        int min = Integer.MAX_VALUE;
        int minNeigh = 0;
        for (Map.Entry<Integer, NeighbourNode> iter : SE.entrySet()) {
            NeighbourNode neighbour = iter.getValue();
            if(neighbour.getWeight() < min){
                min = neighbour.getWeight();
                minNeigh = neighbour.getIndex();
            }
        }

        // update adjacent edge
        SE.get(minNeigh).setSE(State_edge.In_MST);
        LN = 0;
        SN = State_node.Found;
        find_count = 0;
        SE.get(minNeigh).getNode().receive_connect(index, LN);
    }

    /**
     * Awake the node
     */
    public void start() throws RemoteException{
        if(SN == State_node.Sleeping)
            wakeup();
    }

    public State_node getSN() {
        return SN;
    }


    private void handleQueue() throws RemoteException{

        for(int i = 0; i < queue.size(); i ++){
            Message msg = queue.poll();

            switch(msg.getType()){
                case CONNECT: receive_connect(msg.getSrc(), msg.getLevel()); break;
                case TEST: receive_test(msg.getSrc(), msg.getLevel(), msg.getFragment()); break;
                case REPORT: receive_report(msg.getSrc(), msg.getWeight()); break;
            }
        }
    }

    private void halt()throws RemoteException{
        halt = true;
        logger.info("------- Construct MST Finished --------");
        receive_print(index);
    }


}
import org.omg.CORBA.IntHolder;
import org.omg.CORBA.ORB;
import org.omg.CosNaming.NameComponent;
import org.omg.CosNaming.NamingContext;
import org.omg.CosNaming.NamingContextHelper;
import org.omg.CosNaming.NamingContextPackage.CannotProceed;
import org.omg.CosNaming.NamingContextPackage.InvalidName;
import org.omg.CosNaming.NamingContextPackage.NotFound;
import org.omg.PortableServer.POA;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

class Start {
    public static void main(String[] argv) {
        try {
            ORB orb = ORB.init(argv, null);
            POA rootpoa = (POA) orb.resolve_initial_references("RootPOA");
            rootpoa.the_POAManager().activate();

            org.omg.CORBA.Object namingContextObj = orb.resolve_initial_references("NameService");
            NamingContext nCont = NamingContextHelper.narrow(namingContextObj);

            Server cimpl = new Server(nCont);
            org.omg.CORBA.Object ref = rootpoa.servant_to_reference(cimpl);

            System.out.println(orb.object_to_string(ref));

            NameComponent[] path = {new NameComponent("SERVER", "Object")};

            nCont.rebind(path, ref);
            orb.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

class Server extends ServerInterfacePOA {

    private final static Object object = new Object();

    private final NamingContext ncRef;

    private volatile int countThreads = 0;
    private volatile int notFreeThreads = 0;

    private ConcurrentHashMap<User, ConcurrentHashMap<Integer, String>> userAndTasks = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Integer, User> taskIdUserPair = new ConcurrentHashMap<>();

    private Vector<Integer> uniqueCodeUser = new Vector<>();
    private Vector<Integer> uniqueCodeTask = new Vector<>();
    private Enumeration eu = uniqueCodeUser.elements();
    private Enumeration et = uniqueCodeTask.elements();

    Server(NamingContext ncRef) {
        setUniqueIdUser();
        setUniqueIdTask();
        this.ncRef = ncRef;
    }

    private void setUniqueIdUser() {
        IntStream.range(0, 2000).forEach(i -> uniqueCodeUser.add(i));
    }

    private void setUniqueIdTask() {
        IntStream.range(0, 2000).forEach(i -> uniqueCodeTask.add(i));
    }

    private synchronized int getUniqueIdUser() {
        return (int) eu.nextElement();
    }

    private synchronized int getUniqueIdTask() {
        return (int) et.nextElement();
    }

    @Override
    public void setResources(int cores) {
        synchronized (this) {
            countThreads = cores;
        }
    }

    @Override
    public void submit(String[] urls, int tasks, int parallelTasks, IntHolder userID) {

        userID.value = getUniqueIdUser();
        new Thread(() -> {
            User user = new User(parallelTasks);

            ConcurrentHashMap<Integer, String> existingTask;
            if (userAndTasks.get(user) == null) {
                userAndTasks.put(user, new ConcurrentHashMap<>());
                existingTask = userAndTasks.get(user);
            } else {
                existingTask = userAndTasks.get(user);
            }

            for (String url : urls) {
                int id = getUniqueIdTask();
                existingTask.put(id, url);
                taskIdUserPair.put(id, user);
            }

            startTasks();
        }) {{
            start();
        }};
    }

    @Override
    public void done(int taskID) {
        new Thread(() -> {
            User userTaskFinished = taskIdUserPair.get(taskID);
            taskIdUserPair.remove(taskID);
            userTaskFinished.actuallyCountRunTask -= 1;
            notFreeThreads -= 1;

            startTasks();
        }) {{
            start();
        }};
    }

    @Override
    public void cancel(int userID) {
        System.out.println("Cancel");
    }

    private void startTasks() {
        synchronized (object) {
            while (notFreeThreads < countThreads) {

                Map.Entry<User, ConcurrentHashMap<Integer, String>> userWithTasks = getPair();
                if (userWithTasks == null) break;

                ConcurrentHashMap<Integer, String> allTasks = userWithTasks.getValue();
                int userParallelTasks = userWithTasks.getKey().parallelTasks;

                Set<Map.Entry<Integer, String>> entrySet = allTasks.entrySet();
                Iterator<Map.Entry<Integer, String>> itr = entrySet.iterator();

                if (allTasks.size() >= userParallelTasks) {
                    notFreeThreads += userParallelTasks;
                    userWithTasks.getKey().actuallyCountRunTask += userParallelTasks;
                } else {
                    notFreeThreads += allTasks.size();
                    userWithTasks.getKey().actuallyCountRunTask += allTasks.size();
                }

                for (int i = 0; i < userParallelTasks; i++) {
                    Map.Entry<Integer, String> entry = itr.next();
                    new Thread(() -> {
                        try {
                            TaskInterface taskRef = TaskInterfaceHelper.narrow(ncRef.resolve(new NameComponent[]{new NameComponent(entry.getValue(), "Object")}));
                            taskRef.start(entry.getKey());
                        } catch (NotFound | CannotProceed | InvalidName notFound) {
                            notFound.printStackTrace();
                        }
                    }) {{
                        start();
                    }};
                    itr.remove();
                    allTasks.remove(entry.getKey());
                }
            }
        }
    }

    private Map.Entry<User, ConcurrentHashMap<Integer, String>> getPair() {

        int freeThreads = countThreads - notFreeThreads;

        for (Map.Entry<User, ConcurrentHashMap<Integer, String>> pair : userAndTasks.entrySet()) {
            if (pair.getKey().parallelTasks <= freeThreads) {
                if (pair.getKey().checkIfUserHasRunAnyTask()) {
                    if (pair.getKey().parallelTasks <= pair.getValue().size()) {
                       return pair;
                    }
                }
            }
        }

        return  null;
    }
}

class User {
    int parallelTasks;
    volatile int actuallyCountRunTask = 0;

    User(int parallelTasks) {
        this.parallelTasks = parallelTasks;
    }

    boolean checkIfUserHasRunAnyTask() {
        return !(actuallyCountRunTask > 0);
    }
}
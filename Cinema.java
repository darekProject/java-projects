import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class Cinema extends UnicastRemoteObject implements CinemaInterface {

    private ConcurrentMap<String, Seance> films = new ConcurrentHashMap<>();

    Cinema() throws RemoteException {
//        super();
//        setUniqueCode();
    }

    private Seance getSeance(String url) {
        films.putIfAbsent(url, new Seance(url));
        return films.get(url);
    }

    @Override
    public Integer reserveSeats(List<Integer> seats, String url) throws RemoteException {

        Seance seance = getSeance(url);
        if (seance.checkIfUserCanReservedSeats(seats)) {
            return seance.reserve(seats, url);
        } else {
            return null;
        }

    }

    @Override
    public boolean buyTickets(List<Integer> seats, String url) throws RemoteException {

        Seance seance = getSeance(url);
        return seance.checkIfUserCanReservedSeats(seats) && seance.buy(seats, url);

    }

    @Override
    public boolean buyTickets(List<Integer> seats, int reservationCode, String url) throws RemoteException {
        Seance seance = getSeance(url);
        return seance.buy(seats, reservationCode, url);
    }
}

class Seance {
    private Vector<Integer> uniqueCode = new Vector<>();
    private Enumeration e = uniqueCode.elements();
//    private final Vector<Integer> frozenSeats = new Vector<>();
    private final Vector<Integer> unSoldSeats = new Vector<>();
    private final ConcurrentMap<Integer, Integer> film = new ConcurrentHashMap<>();
//    private volatile int last = 0;
    private TicketsInterface ticketsInterface = null;

    Seance(String url) {
        ticketsInterface = Helper.connect(url);
        setUniqueCode();
    }

    private void setUniqueCode() {
        for (int i = 2; i < 2000; i++)
            uniqueCode.add(i);
    }

    private synchronized int getUniqueCode() {
        return (int) e.nextElement();
    }

    boolean checkIfUserCanReservedSeats(List<Integer> seats) throws RemoteException {
        synchronized (film) {
            for (Integer seat : seats) {
                if (film.containsKey(seat)) {
                    return false;
                }
            }
            int code = getUniqueCode();
            for (Integer seat : seats) {
                film.putIfAbsent(seat, code);
            }
            return true;
        }
    }

    Integer reserve(List<Integer> seats, String url) throws RemoteException {

        for (Integer seat : seats) {
            ticketsInterface.reserve(seat);
        }
        return film.get(seats.get(0));
    }

    boolean buy(List<Integer> seats, String url) throws RemoteException {
        for (Integer seat : seats) {
            film.putIfAbsent(seat, 1); // one means that seat was bought
            ticketsInterface.buyTicket(seat);
        }
        return true;

    }

    boolean buy(List<Integer> seats, int reservationCode, String url) throws RemoteException {
        for (Integer seat : seats) {
            if (film.containsKey(seat) && film.get(seat) == reservationCode) {
                ticketsInterface.buyTicket(seat);
                film.put(seat, 1);
            } else if (!film.containsKey(seat)) {
                ticketsInterface.buyTicket(seat);
                film.put(seat, 1);
            } else {
                return false;
            }
        }

        synchronized (unSoldSeats) {
            film.forEach((k, v) -> {
                if (v == reservationCode) {
                    unSoldSeats.add(k);
                }
            });

            for (Integer unSoldSeat : unSoldSeats) {
                film.remove(unSoldSeat);
            }
        }
        return true;
    }
}

class Start {
    public static void main(String[] arg) {
        try {
            Cinema cinema = new Cinema();
            Registry registry = LocateRegistry.getRegistry();
            registry.rebind("CINEMA", cinema);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}


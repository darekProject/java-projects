import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class MoonBase extends Thread implements MoonBaseInterface {

    private final Object ObjectLock = new Object();
    private List<AirlockInterface> availableAirlocks;
    private ArrayList<CargoInterface> cargoListSortedBySize = new ArrayList<>();

    @Override
    public void setAirlocksConfiguration(List<AirlockInterface> airlocks) {
        this.availableAirlocks = airlocks;
    }

    @Override
    public void cargoTransfer(CargoInterface cargo, Direction direction) {
        new Thread(() -> {
            synchronized (ObjectLock) {
                try {
                    cargoListSortedBySize.add(cargo);
                    Collections.sort(cargoListSortedBySize, new Comparator<CargoInterface>() {
                        @Override
                        public int compare(CargoInterface o1, CargoInterface o2) {
                            return Integer.compare(o1.getSize(), o2.getSize());
                        }
                    });
                    Collections.reverse(cargoListSortedBySize);
                    transfers();
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
        }) {{
            start();
        }};
    }

    private List<AirlockInterface> sortAirlock(List<AirlockInterface> airlock) {
        Collections.sort(airlock, new Comparator<AirlockInterface>() {
            @Override
            public int compare(AirlockInterface o1, AirlockInterface o2) {
                return o1.getSize() - o2.getSize();
            }
        });
        return airlock;
    }

    private void transfers() {

        synchronized (ObjectLock) {
            List<AirlockInterface> freeAirlockSortedBySize = sortAirlock(availableAirlocks);
            Iterator<CargoInterface> i = cargoListSortedBySize.iterator();

            while (i.hasNext()) {
                CargoInterface cargo = i.next();
                AirlockInterface goodDoor = null;

                for (AirlockInterface airlock : freeAirlockSortedBySize) {
                    if (airlock.getSize() >= cargo.getSize()) {
                        goodDoor = airlock;
                        break;
                    }
                }

                if (goodDoor != null) {
                    i.remove();
                    availableAirlocks.remove(goodDoor);
                    switch (cargo.getDirection()) {
                        case INSIDE:
                            goodDoor.setEventsListener(EventsListenerInterfaceComeInCase(cargo, goodDoor));
                            goodDoor.openExternalAirtightDoors();
                            break;
                        case OUTSIDE:
                            goodDoor.setEventsListener(EventsListenerInterfaceExitCase(cargo, goodDoor));
                            goodDoor.openInternalAirtightDoors();
                            break;
                    }
                }
            }
        }

    }

    private AirlockInterface.EventsListenerInterface EventsListenerInterfaceComeInCase(CargoInterface cargo,
                                                                                       AirlockInterface airlock) {
        return new AirlockInterface.EventsListenerInterface() {
            public void newAirlockEvent(AirlockInterface.Event event) {
                if (event == AirlockInterface.Event.EXTERNAL_AIRTIGHT_DOORS_OPENED) {
                    if (airlock.getSize() == cargo.getSize()) {
                        airlock.insertCargo(cargo);
                    } else {
                        rockEndRollWithCargo(airlock, cargo);
                    }
                } else if (event == AirlockInterface.Event.CARGO_INSIDE) {
                    airlock.closeExternalAirtightDoors();
                } else if (event == AirlockInterface.Event.EXTERNAL_AIRTIGHT_DOORS_CLOSED) {
                    airlock.openInternalAirtightDoors();
                } else if (event == AirlockInterface.Event.INTERNAL_AIRTIGHT_DOORS_OPENED) {
                    airlock.ejectCargo();
                } else if (event == AirlockInterface.Event.AIRLOCK_EMPTY) {
                    airlock.closeInternalAirtightDoors();
                } else if (event == AirlockInterface.Event.INTERNAL_AIRTIGHT_DOORS_CLOSED) {
                    synchronized (ObjectLock) {
                        availableAirlocks.add(airlock);
                        transfers();
                    }
                } else if (event == AirlockInterface.Event.DISASTER) {
                    throw new IllegalStateException();
                }
            }
        };
    }

    private AirlockInterface.EventsListenerInterface EventsListenerInterfaceExitCase(CargoInterface cargo,
                                                                                     AirlockInterface airlock) {
        return new AirlockInterface.EventsListenerInterface() {
            public void newAirlockEvent(AirlockInterface.Event event) {
                if (event == AirlockInterface.Event.INTERNAL_AIRTIGHT_DOORS_OPENED) {
                    airlock.insertCargo(cargo);
                } else if (event == AirlockInterface.Event.CARGO_INSIDE) {
                    airlock.closeInternalAirtightDoors();
                } else if (event == AirlockInterface.Event.INTERNAL_AIRTIGHT_DOORS_CLOSED) {
                    airlock.openExternalAirtightDoors();
                } else if (event == AirlockInterface.Event.EXTERNAL_AIRTIGHT_DOORS_OPENED) {
                    airlock.ejectCargo();
                } else if (event == AirlockInterface.Event.AIRLOCK_EMPTY)
                    airlock.closeExternalAirtightDoors();
                else if (event == AirlockInterface.Event.EXTERNAL_AIRTIGHT_DOORS_CLOSED) {
                    synchronized (ObjectLock) {
                        availableAirlocks.add(airlock);
                        transfers();
                    }
                } else if (event == AirlockInterface.Event.DISASTER) {
                    throw new IllegalStateException();
                }
            }
        };
    }

    private void rockEndRollWithCargo(AirlockInterface airlock, CargoInterface cargo) {
        synchronized (ObjectLock) {
            CargoInterface tmpCargoInside = null;
            CargoInterface tmpCargoOutside = null;

            for (CargoInterface cargoOfList : cargoListSortedBySize) {
                if (cargoOfList.getSize() == airlock.getSize() && cargoOfList.getDirection() == Direction.INSIDE) {
                    tmpCargoInside = cargoOfList;
                    break;
                }
            }

            for (CargoInterface cargoOfList : cargoListSortedBySize) {
                if (cargoOfList.getSize() == airlock.getSize() && cargoOfList.getDirection() == Direction.OUTSIDE) {
                    tmpCargoOutside = cargoOfList;
                    break;
                }
            }
            if (tmpCargoInside == null && tmpCargoOutside == null) {
                airlock.insertCargo(cargo);
            }

            if (tmpCargoInside == null) {
                if (tmpCargoOutside != null) {
                    cargoListSortedBySize.add(cargo);
                    Collections.sort(cargoListSortedBySize, new Comparator<CargoInterface>() {
                        @Override
                        public int compare(CargoInterface o1, CargoInterface o2) {
                            return Integer.compare(o1.getSize(), o2.getSize());
                        }
                    });
                    Collections.reverse(cargoListSortedBySize);
                    airlock.setEventsListener(EventsListenerInterfaceExceptionalCase(airlock));
                    airlock.closeExternalAirtightDoors();
                }
            } else {
                cargoListSortedBySize.add(cargo);
                Collections.sort(cargoListSortedBySize, new Comparator<CargoInterface>() {
                    @Override
                    public int compare(CargoInterface o1, CargoInterface o2) {
                        return Integer.compare(o1.getSize(), o2.getSize());
                    }
                });
                Collections.reverse(cargoListSortedBySize);
                cargoListSortedBySize.remove(tmpCargoInside);
                airlock.insertCargo(tmpCargoInside);
            }
        }
    }

    private AirlockInterface.EventsListenerInterface EventsListenerInterfaceExceptionalCase(AirlockInterface airlock) {
        return new AirlockInterface.EventsListenerInterface() {
            public void newAirlockEvent(AirlockInterface.Event event) {
                if (event == AirlockInterface.Event.INTERNAL_AIRTIGHT_DOORS_OPENED
                        || event == AirlockInterface.Event.EXTERNAL_AIRTIGHT_DOORS_CLOSED) {
                    synchronized (ObjectLock) {
                        availableAirlocks.add(airlock);
                        transfers();
                    }
                } else if (event == AirlockInterface.Event.DISASTER) {
                    throw new IllegalStateException();
                }
            }
        };
    }

}

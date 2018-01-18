import java.awt.*;
import java.awt.geom.Point2D;
import java.util.NavigableMap;
import java.util.Vector;
import java.util.concurrent.*;

public class MotionDetectionSystem implements MotionDetectionSystemInterface {

    private static final Object lock = new Object();
    private ImageConverterInterface providedImageConverter = null;
    private ResultConsumerInterface providedListener = null;
    private ConcurrentNavigableMap<Integer, int[][]> providedImage = new ConcurrentSkipListMap<>();
    private ConcurrentMap<Integer, Point2D.Double> resultToSend = new ConcurrentHashMap<>();
    private Vector<Integer> processingImg = new Vector<>();
    private final BlockingQueue<Runnable> tasks = new LinkedBlockingQueue<>();

    private Integer frameSent = -1;

    @Override
    public void setImageConverter(ImageConverterInterface ici) {
        this.providedImageConverter = ici;
    }

    @Override
    public void setResultListener(ResultConsumerInterface rci) {
        this.providedListener = rci;
    }

    private ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 1, TimeUnit.SECONDS, tasks);

    @Override
    public void setThreads(int threads) {
        if (threads > executor.getMaximumPoolSize()) {
            executor.setMaximumPoolSize(threads);
            executor.setCorePoolSize(threads);
        } else {
            executor.setCorePoolSize(threads);
            executor.setMaximumPoolSize(threads);
        }

    }

    @Override
    public void addImage(int frameNumber, int[][] image) {

        providedImage.put(frameNumber, image);
        executor.submit(this::checkDetection);

    }

    private void checkDetection() {

        synchronized (lock) {
            for (Integer keyImages : providedImage.keySet()) {

                if (providedImage.containsKey(keyImages + 1) && !processingImg.contains(keyImages) && keyImages > frameSent) {
                    processingImg.add(keyImages);
                    executor.submit(() -> {
                        Point2D.Double resultConvert = providedImageConverter.convert(keyImages, providedImage.get(keyImages), providedImage.get(keyImages + 1));
                        resultToSend.put(keyImages, resultConvert);
                        executor.submit(this::sendResultIfYouCan);
                    });
                }
            }
        }

    }


    private synchronized void sendResultIfYouCan() {
        for (int i = 0; i <= frameSent + 1; i++) {
            if (resultToSend.containsKey(frameSent + 1)) {
                providedListener.accept(++frameSent, resultToSend.get(frameSent));
            }
        }
    }
}
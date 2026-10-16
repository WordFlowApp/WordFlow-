    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }

        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }

        if (panel != null && wm != null) {
            try {
                wm.removeView(panel);
            } catch (Exception ignored) {
            }
        }

        worker.shutdownNow();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

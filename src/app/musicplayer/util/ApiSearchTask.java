package app.musicplayer.util;

import app.musicplayer.util.ApiService;
import javafx.concurrent.Task;

public class ApiSearchTask extends Task<Void> {
    private final String searchTerm;
    private final Runnable onSuccess;
    private final Runnable onFailure;

    public ApiSearchTask(String searchTerm, Runnable onSuccess, Runnable onFailure) {
        this.searchTerm = searchTerm;
        this.onSuccess = onSuccess;
        this.onFailure = onFailure;
    }

    @Override
    protected Void call() throws Exception {
        ApiService.searchAndSave(searchTerm);
        return null;
    }

    @Override
    protected void succeeded() {
        onSuccess.run();
    }

    @Override
    protected void failed() {
        onFailure.run();
    }
}
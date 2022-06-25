package ru.javaops.masterjava.matrix;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import ru.javaops.masterjava.service.MailService;

public class MatrixService {
    private final ExecutorService mailExecutor = Executors.newFixedThreadPool(8);

    public MailService.GroupResult multiplyMatrices(final String template, final Set<String> emails) throws Exception {
        final CompletionService<MailService.MailResult> completionService = new ExecutorCompletionService<>(mailExecutor);

        List<Future<MailService.MailResult>> futures = emails.stream()
                .map(email -> completionService.submit(() -> sendToUser(template, email)))
                .collect(Collectors.toList());

        return new Callable<MailService.GroupResult>() {
            private int success = 0;
            private List<MailService.MailResult> failed = new ArrayList<>();

            @Override
            public MailService.GroupResult call() {
                while (!futures.isEmpty()) {
                    try {
                        Future<MailService.MailResult> future = completionService.poll(10, TimeUnit.SECONDS);
                        if (future == null) {
                            return cancelWithFail(INTERRUPTED_BY_TIMEOUT);
                        }
                        futures.remove(future);
                        MailService.MailResult mailResult = future.get();
                        if (mailResult.isOk()) {
                            success++;
                        } else {
                            failed.add(mailResult);
                            if (failed.size() >= 5) {
                                return cancelWithFail(INTERRUPTED_BY_FAULTS_NUMBER);
                            }
                        }
                    } catch (ExecutionException e) {
                        return cancelWithFail(e.getCause().toString());
                    } catch (InterruptedException e) {
                        return cancelWithFail(INTERRUPTED_EXCEPTION);
                    }
                }
/*
                for (Future<MailResult> future : futures) {
                    MailResult mailResult;
                    try {
                        mailResult = future.get(10, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        return cancelWithFail(INTERRUPTED_EXCEPTION);
                    } catch (ExecutionException e) {
                        return cancelWithFail(e.getCause().toString());
                    } catch (TimeoutException e) {
                        return cancelWithFail(INTERRUPTED_BY_TIMEOUT);
                    }
                    if (mailResult.isOk()) {
                        success++;
                    } else {
                        failed.add(mailResult);
                        if (failed.size() >= 5) {
                            return cancelWithFail(INTERRUPTED_BY_FAULTS_NUMBER);
                        }
                    }
                }
*/
                return new MailService.GroupResult(success, failed, null);
            }

            private MailService.GroupResult cancelWithFail(String cause) {
                futures.forEach(f -> f.cancel(true));
                return new MailService.GroupResult(success, failed, cause);
            }
        }.call();
    }
}

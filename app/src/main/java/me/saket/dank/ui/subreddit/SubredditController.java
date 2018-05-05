package me.saket.dank.ui.subreddit;

import static io.reactivex.android.schedulers.AndroidSchedulers.mainThread;
import static io.reactivex.schedulers.Schedulers.io;

import net.dean.jraw.models.Submission;

import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

import dagger.Lazy;
import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.ObservableTransformer;
import me.saket.dank.data.DankRedditClient;
import me.saket.dank.data.InboxRepository;
import me.saket.dank.ui.UiChange;
import me.saket.dank.ui.UiEvent;
import me.saket.dank.ui.subreddit.events.SubredditScreenCreateEvent;
import me.saket.dank.ui.subreddit.events.SubredditSubmissionClickEvent;
import me.saket.dank.ui.user.UserProfileRepository;
import me.saket.dank.ui.user.UserSessionRepository;
import me.saket.dank.ui.user.messages.InboxFolder;
import me.saket.dank.utils.DankSubmissionRequest;
import me.saket.dank.utils.Pair;

public class SubredditController implements ObservableTransformer<UiEvent, UiChange<SubredditUi>> {

  private final Lazy<InboxRepository> inboxRepository;
  private final Lazy<UserProfileRepository> userProfileRepository;
  private final Lazy<UserSessionRepository> userSessionRepository;

  private boolean applied;

  @Inject
  public SubredditController(
      Lazy<InboxRepository> inboxRepository,
      Lazy<UserProfileRepository> userProfileRepository,
      Lazy<UserSessionRepository> userSessionRepository)
  {
    this.inboxRepository = inboxRepository;
    this.userProfileRepository = userProfileRepository;
    this.userSessionRepository = userSessionRepository;
  }

  @Override
  public ObservableSource<UiChange<SubredditUi>> apply(Observable<UiEvent> upstream) {
    if (applied) {
      throw new AssertionError("This controller can only be used in compose() once.");
    }
    applied = true;

    Observable<UiEvent> replayedEvents = upstream.replay().refCount();

    //noinspection unchecked
    return Observable.mergeArray(
        unreadMessageIconChanges(replayedEvents),
        submissionExpansions(replayedEvents));
  }

  private Observable<UiChange<SubredditUi>> unreadMessageIconChanges(Observable<UiEvent> events) {
    return events
        .ofType(SubredditScreenCreateEvent.class)
        .switchMap(o -> userSessionRepository.get().streamSessions())
        .filter(session -> session.isPresent())
        .switchMap(session -> {
          Observable<Integer> unreadCountsFromAccount = userProfileRepository.get()
              .loggedInUserAccounts()
              .map(account -> account.getInboxCount());

          Observable<Integer> unreadCountsFromInbox = inboxRepository.get()
              .messages(InboxFolder.UNREAD)
              .map(unreads -> unreads.size());

          return unreadCountsFromInbox.mergeWith(unreadCountsFromAccount)
              .subscribeOn(io())
              .observeOn(mainThread())
              .map(unreadCount -> unreadCount > 0)
              .map(hasUnreads -> hasUnreads
                  ? SubredditUserProfileIconType.USER_PROFILE_WITH_UNREAD_MESSAGES
                  : SubredditUserProfileIconType.USER_PROFILE)
              .map(iconType -> ui -> ui.setToolbarUserProfileIcon(iconType));
        });
  }

  private Observable<UiChange<SubredditUi>> submissionExpansions(Observable<UiEvent> events) {
    Observable<String> subredditNameChanges = events
        .ofType(SubredditChangeEvent.class)
        .map(event -> event.subredditName());

    Observable<UiChange<SubredditUi>> populations = events
        .ofType(SubredditSubmissionClickEvent.class)
        .withLatestFrom(subredditNameChanges, Pair::create)
        .map(pair -> {
          SubredditSubmissionClickEvent clickEvent = pair.first();
          String currentSubredditName = pair.second();
          Submission submission = clickEvent.submission();

          DankSubmissionRequest submissionRequest = DankSubmissionRequest.builder(submission.getId())
              .commentSort(submission.getSuggestedSort() != null ? submission.getSuggestedSort() : DankRedditClient.DEFAULT_COMMENT_SORT)
              .build();

          return (UiChange<SubredditUi>) ui -> ui.populateSubmission(submission, submissionRequest, currentSubredditName);
        });

    Observable<UiChange<SubredditUi>> expansions = events
        .ofType(SubredditSubmissionClickEvent.class)
        // This delay ensures that they submission is almost
        // ready to be shown and will not stutter while expanding.
        .delay(100, TimeUnit.MILLISECONDS, mainThread())
        .map(event -> (UiChange<SubredditUi>) ui -> ui.expandSubmissionRow(event.itemView(), event.itemId()));

    return populations.mergeWith(expansions);
  }
}

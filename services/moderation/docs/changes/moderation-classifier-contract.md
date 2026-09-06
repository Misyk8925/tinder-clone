# Moderation classifier contract

## Outcome

- Represent text, image, locale, country, author, content type, and conversation context in `ModerationContent`.
- Replace the text-only `MlPort`/`MlResult` contract with a provider-neutral `ModerationClassifierPort` and category-based `ClassificationResult`.
- Preserve the existing moderation policy by adapting supported category results into its current toxicity and spam signals.

## Out of scope

- A real OpenAI, Azure, or other vendor adapter.
- Persistence of classifier evidence.
- Versioned policies and category-specific decision rules.
- HTTP or event adapters.

## Acceptance evidence

### Moderation content

- Text-only and image-only content are accepted.
- Content without non-blank text or images is rejected.
- Id, type, text, image URLs, locale, country, author, and ordered conversation context are retained.
- Blank content ids, image URLs, locale, country, and author ids are rejected.
- Blank context content ids, author ids, and text are rejected.
- The supported content-type vocabulary is fixed by an executable check.

### Classifier contract

- The use case passes the complete multimodal content object to the classifier.
- Image-only content reaches the classifier without invoking the text-only LLM.
- Provider, model, optional snapshot, category scores, aggregate flag, and latency are retained.
- Blank provider/model/snapshot, empty category maps, and negative latency are rejected.
- Supported categories require a bounded score; unsupported categories have no score and cannot be flagged.
- Every result must explicitly contain the complete moderation-category vocabulary, using unsupported entries where necessary.
- The aggregate classifier flag must agree with the category flags.
- The supported moderation-category vocabulary is fixed by an executable check.
- The compatibility mapper selects the highest supported toxicity score, maps positive and negative spam flags, and does not invent a legacy decision for currently unmapped categories.
- No source reference to `MlPort`, `MlResult`, `fromMl`, or the old `Content` type remains.

Validation command:

```shell
./gradlew test --tests '*ModerationContentTest' --tests '*ClassificationResultTest' --tests '*SignalBuilderTest' --tests '*ModerateContentUsecaseTest'
./gradlew clean test
rg -n '\bMlPort\b|\bMlResult\b|fromMl\(|domain\.model\.Content\b|\bContent\(' src
git diff --check
```

## Rollback

Revert this change as one slice, restoring `Content`, `MlPort`, and `MlResult` together; the old and new contracts must not be mixed.

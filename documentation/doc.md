# Documentation

## Product Thinking

### How does your solution improve on traditional spell checkers?

It can harmonize the jargon of the speaker and make corrections while keeping the short structure of speaker notes.

It can easily be extended to spell-check for typical abbreviations used in speaker decks.

It could handle text in multiple languages (depending on the language used, only certain languages are supported by LLMs).

It could connect to the slide (i.e., this part of the notes is for this slide).

### When should AI be invoked vs. using traditional methods?

Naively, a first pass should be done using traditional techniques to avoid latency and cost.

Then traditional NLP techniques, using OpenNLP, can be used to detect nouns and do a fuzzy fix between the context and the notes.

An additional AI action could be added to run the AI spell checking. If run on multiple slides at once, it needs to come bundled with a thoughtful UX to let the user review/revert the changes, or more generally stay in control of the changes done.

A practical approach would be to have a first pass in the browser with traditional techniques and, since the LLM takes only a few seconds using the Google free tier, use AI to fix the rest.

Use a debounce to call the AI when the user is done typing, otherwise the latency will be degrade the UX.


### What's the user experience for accepting/rejecting suggestions?

In the prototype, the user needs to approve each of the suggestions.

The prototype could be easily improved to offer a YOLO mode: accept all suggestions.

A further improvement is to approve all similar suggestions. For example, LeanMews to LeanNews. I think it's more involved because the LLM needs to be prompted to explain the suggestion, as certain changes would be contextual to the phrase they are in.


## Written Component

An LLM is perfectly capable of using context and checking spelling mistakes and consistency using a simple prompt and small model.

Points of attention are:
- Preventing the LLM from adding extra content or using a different tone.
- Keeping the language of origin of the notes (English, German, French, ...).
- Defining an output schema for the LLM that allows for in-line editing of the text. The format needs to be flexible enough to allow for multiple types of editing (YOLO, per word, multiple words, ...). Alongside the output schema, an eval function needs to be put in place to catch an LLM not following the schema. Try to keep the prompt small to prevent an extra latency tax.
- Keeping the UX fast despite the slowness of the LLM.
- Keeping the user in control of the AI fix: the user needs to know what those suggestions are and be able to revert them easily.
- Monitoring carefully the usage from a technical point of view (retry number, token output, rate limit, cost...) and from a user point of view (how much the feature is used, suggestions accepted, user in YOLO mode) as it will be easy to get lost in technical improvements that are not increasing the usage of the spell checker.

### General idea of an effective spell checker

Be local-first, using a JS library for example.
Then use a backend solution for fuzzy search and user dictionary.
Finally use an LLM.

The general idea is to not use the LLM to prevent latency for "regular" spell check.

The debounce value needs to be experimented with. When a slide is changed and when the slide notes are changed.

### The return format

I decided to return the entire text interleaved with the corrections.
This format offers the advantage of being:
- easily parseable
- evaluation friendly
- extendable: the JSON object can contain more properties, like the reason of the suggestion.

This format is also fragile, for example, if the slide contains JSON, then it will break. A similar ad-hoc JSON format could be defined as the LLM output or the slide note should encode the JSON.

In any case, the output needs to be carefully checked => better to have no spelling check than suggesting non-sense.

Example of answer:

```
# projeckt -> project
"The `{:origin "projeckt", :sugg "project"}` failed."

# No suggestion
"The project failed."
```

### Using CLJC for the parser

Sharing the codebase between Frontend and Backend ensures that the response returned to the user will evaluated like it will be used.


### Trade-offs you considered

I started with a fast and cheap model (gemini-2.5-flash-lite) to see if it could work, later I switched to a more advanced one (gemini-2.5-flash) able to handle the data I used to evaluate the solution. However, the latency introduced can be up to 8s. Not ideal but fast enough to be considered.

I decided to pick a defensive output format to be more certain of the LLM output. For example, I experimented with Mistral, which occasionally prefixes the spell checking with text (for example: "Here are your suggestions") despite being asked not to do that. There is also the possibility that the Slide content implies other changes to the output.

Using the string interleaved with JSON, I can confirm that if I apply all the `:origin` the text is similar to the original one and If I apply all the `:sugg` the text should be different. If the confirmation fail, I can gracefully respond to th euser and logging miss spell checking for further improvement of the system.

I used Gemini for simplicity but I believe the problem scope and the output format are simple enough that a self-hosted solution could be used.

For a production setup, the retry policy need to be less naive and used a persistant engine workflow.


#### Cost and latency optimization (not part of this prototype)

Having a LRU Cache on the backend will save a lot of tokens and improve the latency.

Certain LLM providers provide prompt caching, that could also be used by splitting the prompt into multiple messages. I have not used that myself. Note that the community feedback on those features has been mixed, with users claiming that it creates an additional state that not always behaves like they would expect.

### Offline support

Keeping the traditional frontend spell checking working offline is possible.

Outside the scope of this prototype, the application should keep track of the Speaker Notes that have been spell checked and run the spell check once the application is back online.


### Ideas for improvements

#### Dictionary

Speaker notes probably have a general jargon, the user may have a jargon too and the jargon could be enhance with an LLM call to extract the general context of the presentation. Those could be passed as additional context inside the prompt. The downside will be an increasing prompt.

However, as a first approach, I will try to substitute the text with words for the dictionary before sending the text to the LLM. For example if a user wants to use "Read nts on quarter" instead of "Read notes on quarter", the text sent to the LLM will be:
```
Read `{"approve": "nts", "original": "nts"}` on quarter
```
A analogue approach is to keep the prompt as it is but apply the dictionary on it's output. In the example above, the LLM will suggest "notes" instead of "nts" and the code will automaticly discard the suggestions by checking the dictionary.

That will prevent the prompt to grow with the full dictionary and jargon. This will also prevent prompt injection.

#### Privacy

The problem space and the simple return format should make it possible to use a self-hosted model for full privacy.

A more involved idea is to translate brand names and other sensitive data before sending the text to an LLM. While not bulletproof, it will improve the privacy at the cost of more complexity in the output format and parser.

#### CRDT for collaborating editing

If the product has already some facility for CRDT, the prompt output could be modified to be CRDT friendly. This will make the notes editing support concurrent editing without losing the defensive eval system.

#### Make the spell check support multiple languages

A note in the prompt could be added to answer in the language of the speaker notes.
Some considerations:
- Not all languages are supported by LLMs and this support is per LLM basis.
- If multiple languages are used it will be tricky to prevent the languages being mixed up.
- A separate LLM call is possible to detect the language but from experience it's not 100% accurate.


## Evaluation Strategy

### How would you test/evaluate this feature's effectiveness?

I will track down a few metrics.

Technical metrics:

- Number of prompt output failing the evaluation
- Number of tokens used (prompt, total, thoughts) depending on what is available
- Number of LLM retries
- Evolution of the rate limit (depending on the LLM provider)
- LLM latency

Usage metrics:

- Enable/disable spell check
- Number of typos found
- Number of typos accepted and refused
- Number of users using the YOLO mode or using the detail review

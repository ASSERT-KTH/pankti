### Testing pankti

We use [jitsi-videobridge v2.1](https://github.com/jitsi/jitsi-videobridge/tree/v2.1) as a test resource to verify that 
pankti finds pure methods as per our definition of purity. The results can be summarized as under.

Of the 893 methods in the test resource, 105 (11.7%) were found to be pure.
The number of pure methods

returning a primitive | returning an object | with parameters | with if conditions | with conditional operators | with loops | with local variables | with switch cases | with multiple statements
--------------------- | ------------------- | --------------- | ------------------ | -------------------------- | ---------- | -------------------- | ----------------- | ------------------------
48 | 57 | 16 | 4 | 4 | 0 | 2 | 1 | 4

A more detailed summary with all pure methods can be found [here](https://github.com/Deee92/journal/blob/master/notes/jitsi-analysis.md).

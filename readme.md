//TODO code cleanup and abstraction into generalised bot methods\
//TODO general efficiency improvements for board and bots\
//TODO start on server\
//TODO game record and replay features (may require account and database features - spring JPA?)\
//TODO shorten sounds\
//TODO add chess format tournaments with many players (not just 2)\
//TODO Add a way on the board view when playing tournaments or things with many games going on to switch to a view where we can see the current evaluation of every game, (maybe even every board)\
//TODO code reformatting and method and UI extraction\
//TODO better tournament layout etc to shwo who goes through\
//TODO log colours to make it more readable\
//TODO last move square highlighting
claude TODO:
- Optimize AdvancedEvaluator so its terms are cheap enough to be a net win (e.g. only run king-zone scans when a queen is on  
  the board; precompute attack tables) — then it could become the default.
- SEE-based move ordering (not just quiescence pruning) in the main search.
- The opening book is actually large (58k lines) and now used to move 16, so that lever is largely spent.    


#/bin/bash
# This is an example on how to call cpimport.
# Either call cpimport using the full path, or by putting it into PATH.
# Please note that separator and enclosing character should NOT be quoted.
# E.G. cpimport -j 3000 -E '"' should be written cpimport -j 3000 -E "
/usr/local/bin/cpimport %%STAGE_SCHEMA%% %%STAGE_TABLE%% %%CSV_FILE%% -s , -E "
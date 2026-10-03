Replace included file from repository root.
TV crash dialog: log pane is focusable with visible border. Up from any action button enters the log. Expanding Details focuses the log. Up/Down scroll in 96dp steps; Down at bottom or OK returns to Details. Left/Right moves among action buttons as before. Copy Log still copies the complete report. Close/dismiss handling is unchanged.
This fixes dialog navigation, not the underlying Home ANR.
Static delimiter checks and ZIP integrity passed. No build or device test performed.
Test long logs, short logs, repeated/held D-pad keys, expand/collapse, Copy Log and Close.

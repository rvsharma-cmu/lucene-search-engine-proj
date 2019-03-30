err_occur = []                         # The list where we will store results.
substr = "map"                        # Substring to use for search.
try:                              # Try to:
	with open ('test.txt', 'rt') as in_file:        # open file for reading text.
		for linenum, line in enumerate(in_file):    # Keep track of line numbers
			if line.lower().find(substr) != -1: #If case-insensitive substring search matches, then:
				err_occur.append((linenum, line.rstrip('\n'))) # strip linebreaks, store line and line number in list as tuple.
		for linenum, line in err_occur:              # Iterate over the list of tuples, and
			print("Line ", linenum, ": ", line, sep='')  # print results as "Line [linenum]: [line]".
except FileNotFoundError:                   # If log file not found,
	print("Log file not found.")                # print an error message.


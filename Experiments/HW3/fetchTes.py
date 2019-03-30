#!/usr/bin/python

#
#  This python script illustrates fetching information from a CGI program
#  that typically gets its data via an HTML form using a POST method.
#
#  Copyright (c) 2018, Carnegie Mellon University.  All Rights Reserved.
#

import requests

#  ===> FILL IN YOUR PARAMETERS <===

userId = 'rvsharma@andrew.cmu.edu'
password = 'tXvSYGdl'
fileIn = '/Users/raghavs/11642/QryEval/Experiments/HW3/OUTPUT_DIR/HW3.2-QryExpRefFBdocs10.teIn'

hwId = 'HW3'
qrels = 'topics.701-850.qrel'

#  Form parameters - these must match form parameters in the web page

url = 'https://boston.lti.cs.cmu.edu/classes/11-642/HW/HTS/tes.cgi'
values = { 'hwid' : hwId,				# cgi parameter
	   'qrel' : qrels,				# cgi parameter
           'logtype' : 'Summary',			# cgi parameter
	   'leaderboard' : 'No'				# cgi parameter
           }

#  Make the request

files = {'infile' : (fileIn, open(fileIn, 'rb')) }	# cgi parameter
result = requests.post (url, data=values, files=files, auth=(userId, password))

#  Replace the <br /> with \n for clarity

print (result.text.replace ('<br />', '\n'))

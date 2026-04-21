# SPDX-FileCopyrightText: 2022 Google LLC
# SPDX-License-Identifier: Apache-2.0

# Called by the script process-codelab-source-files.sh.
# Argument is the path to the source file to process.

import re
import sys

if len(sys.argv) < 2:
    sys.exit("Error: Please specify the name of the file to process.")

file = sys.argv[1]

codelabStartPattern = '// CODELAB: '
codelabEndPattern = '// CODELAB SECTION END'

lines = []

with open(file, 'rt') as infile:
    discard = False
    for line in infile:
        if discard:
          if codelabEndPattern in line:
            discard = False
        else:
          lines.append(line)
          if codelabStartPattern in line:
                discard = True

with open(file, 'w') as outfile:
    for line in lines:
        outfile.write(line)

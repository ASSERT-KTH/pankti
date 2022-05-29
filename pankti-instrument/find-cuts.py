import pandas as pd
import sys

def find_cuts(extracted):
  extracted_cuts = extracted['parent-FQN'].value_counts().sort_values(ascending=False)
  f = open("./cuts.txt", "w")
  f.write(str(extracted_cuts))
  f.close()

def find_cuts_mockables(extracted):
  extracted_muts_with_mockables = extracted[extracted['has-mockable-invocations'] == True]
  extracted_cuts = extracted_muts_with_mockables['parent-FQN'].value_counts().sort_values(ascending=False)
  f = open("./cuts-mockables.txt", "w")
  f.write(str(extracted_cuts))
  f.close()

def main(argv):
  try:
    pd.set_option('display.max_rows', None)
    extracted = pd.read_csv(argv[1])
    print("input (rows, columns): ", extracted.shape)
    cuts = find_cuts(extracted)
    print("List of classes with most methods saved in ./cuts.txt")
    cuts_mockables = find_cuts_mockables(extracted)
    print("List of classes with the methods that have most mockable methods saved in ./cuts-mockables.txt")
  except Exception as e:
    print("USAGE: python instrument.py </path/to/extracted/method/list>.csv")
    print(e)
    sys.exit()

if __name__ == "__main__":
  main(sys.argv)

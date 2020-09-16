import pandas as pd
import re
import sys

def extract_from_tags(tag, tags):
  search_string = tag + "=[\w]+"
  extracted_tag = re.findall(search_string, tags)[0]
  extracted_tag = re.findall("\=(.*)", extracted_tag)[0]
  if extracted_tag == "true":
    return True
  else:
    return False

def create_final_df(df, cols):
  final_df = pd.DataFrame(columns = cols)
  for index, row in df.iterrows():
    final_df.loc[index, 'visibility'] = row['visibility']
    final_df.loc[index, 'parent-FQN'] = row['parent-FQN']
    final_df.loc[index, 'method-name'] = row['method-name']
    final_df.loc[index, 'param-list'] = row['param-list'].lstrip('[').rsplit(']', 1)[0]
    final_df.loc[index, 'return-type'] = row['return-type']
    final_df.loc[index, 'local-variables'] = extract_from_tags("local_variables", str(row['tags']))
    final_df.loc[index, 'conditionals'] = extract_from_tags("conditionals", str(row['tags']))
    final_df.loc[index, 'multiple-statements'] = extract_from_tags("multiple_statements", str(row['tags']))
    final_df.loc[index, 'loops'] = extract_from_tags("loops", str(row['tags']))
    final_df.loc[index, 'parameters'] = extract_from_tags("parameters", str(row['tags']))
    final_df.loc[index, 'returns'] = extract_from_tags("returns", str(row['tags']))
    final_df.loc[index, 'switches'] = extract_from_tags("switches", str(row['tags']))
    final_df.loc[index, 'ifs'] = extract_from_tags("ifs", str(row['tags']))
    final_df.loc[index, 'static'] = extract_from_tags("static", str(row['tags']))
    final_df.loc[index, 'returns-primitives'] = extract_from_tags("returns_primitives", str(row['tags']))
  # print(final_df.head())
  return final_df

def find_instrumentation_candidates(final_df, cols):
  instrumentation_candidates_df = pd.DataFrame(columns = cols)
  instrumentation_candidates_df =  final_df[((final_df['multiple-statements'] == True) |
  (final_df['ifs'] == True) | (final_df['conditionals'] == True) |
  (final_df['parameters'] == True) | (final_df['switches'] == True) |
  (final_df['loops'] == True) | (final_df['local-variables'] == True)) & (final_df['static'] == False)]
  print("output (rows, columns): ", instrumentation_candidates_df.shape)
  instrumentation_candidates_df.to_csv(r'./instrumentation-candidates.csv', index=False)
  print("instrumentation candidates saved in ./instrumentation-candidates.csv")

def main(argv):
  try:
    cols = ["visibility", "parent-FQN", "method-name", "param-list", "return-type",
    "local-variables", "conditionals", "multiple-statements", "loops", "parameters",
    "returns","switches", "ifs", "static", "returns-primitives"]
    df = pd.read_csv(argv[1])
    print("input (rows, columns): ", df.shape)
    final_df = create_final_df(df, cols)
    find_instrumentation_candidates(final_df, cols)
  except:
    print("USAGE: python filter.py </path/to/method/list>.csv")
    sys.exit()

if __name__ == "__main__":
  main(sys.argv)

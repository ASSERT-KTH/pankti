import json
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
  df.fillna('', inplace=True)
  for index, row in df.iterrows():
    final_df.loc[index, 'visibility'] = row['visibility']
    final_df.loc[index, 'parent-FQN'] = row['parent-FQN']
    final_df.loc[index, 'method-name'] = row['method-name']
    final_df.loc[index, 'param-list'] = row['param-list'].lstrip('[').rsplit(']', 1)[0]
    final_df.loc[index, 'return-type'] = row['return-type']
    final_df.loc[index, 'nested-invocations'] = row['nested-invocations']
    final_df.loc[index, 'param-signature'] = row['param-signature']
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
  return final_df.sort_values(by=['parent-FQN', 'method-name'])

def find_instrumentation_candidates(final_df, cols, name, json_files):
  instrumentation_candidates_df = pd.DataFrame(columns = cols)
  for json_file in json_files:
    print("Finding pseudo-tested methods in", json_file)
    with open(json_file, 'r') as f:
      descartes_output = json.load(f)
      for i in range(len(descartes_output['methods'])):
        method = descartes_output['methods'][i]
        if (method['classification'] == "pseudo-tested"):
          parent_fqn = method['package'].replace('/', '.') + '.' + method['class']
          method_name = method['name']
          param_signature = re.search('\((.*)\)', method['description']).group(1)
          if not param_signature:
            param_signature = ""
          final_df.loc[(final_df['parent-FQN'] == parent_fqn) & (final_df['method-name'] == method_name) &
                       (final_df['param-signature'] == param_signature), 'classification'] = method['classification']
          instrumentation_candidates_df = instrumentation_candidates_df.append(
            final_df.loc[(final_df['parent-FQN'] == parent_fqn) &
                         (final_df['method-name'] == method_name) &
                         (final_df['param-signature'] == param_signature)], sort=False)
  instrumentation_candidates_df.sort_values(by=['parent-FQN', 'method-name'], inplace=True)
  file_name = name.replace("extracted-methods", "instrumentation-candidates")
  instrumentation_candidates_df.to_csv(r'./' + file_name, index=False)
  print(len(instrumentation_candidates_df), "instrumentation candidates saved in ./" + file_name)

def main(argv):
  try:
    name = argv[1]
    json_files = list(argv[2:])
    cols = ["visibility", "parent-FQN", "method-name", "param-list", "return-type",
            "nested-invocations", "param-signature", "local-variables", "conditionals",
            "multiple-statements", "loops", "parameters", "returns","switches",
            "ifs", "static", "returns-primitives"]
    df = pd.read_csv(name)
    print("input (rows, columns):", df.shape)
    final_df = create_final_df(df, cols)
    find_instrumentation_candidates(final_df, cols, name, json_files)
  except Exception as e:
    print("USAGE: python find-pseudo-tested.py </path/to/method/list>.csv </space/separated/paths/to/descartes/json/output/files>")
    print(e)
    sys.exit()

if __name__ == "__main__":
  main(sys.argv)

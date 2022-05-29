import glob
import numpy as np
import pandas as pd
import re
import sys


def sanitize_parameter_list(param_list):
  parameters = param_list.replace("(", "").replace(")", "")
  parameters = re.sub(r"([a-zA-Z0-9\$\.\[\]]+)", "\"" + r"\1" + "\"", parameters)
  parameters = re.sub("\"[EKNTV]\"", "\"java.lang.Object\"", parameters)
  parameters = re.sub("\"[EKNTV]\[\]\"", "\"java.lang.Object[]\"", parameters)
  return parameters


# generate aspect classes based on the MethodAspect0 template
def generate_aspects(df, cut):
  base_path = "./src/main/java/se/kth/castor/pankti/instrument/plugins/MethodAspect"
  found_aspects = sorted(glob.glob(base_path + "*.java"), key=lambda x:float(re.findall("(\d+)",x)[0]))
  count = int(re.search(r"(\d+)", found_aspects[-1]).group())
  aspects = []
  template_file_path = base_path + str(0) + ".java"
  df.replace(np.nan, '', regex=True, inplace=True)
  for index, row in df.iterrows():
    if (row['parent-FQN'] == cut if cut else True):
      with open(template_file_path) as template:
        count += 1
        aspect_string = "\"se.kth.castor.pankti.instrument.plugins.MethodAspect" + str(count) + "\""
        aspects.append(aspect_string)
        new_file_path = base_path + str(count) + ".java"
        with open(new_file_path, "w") as f:
          for line in template:
            if (row['param-list'].startswith('[') and row['param-list'].endswith(']')):
              row['param-list'] = re.sub(r"^\[", "", row['param-list'])
              row['param-list'] = re.sub(r"\]$", "", row['param-list'])
            if ("public class MethodAspect0" in line):
              line = line.replace("0", str(count))
            if ("@Pointcut(className =" in line):
              line = re.sub(r"=\s\"(.+)\",", "= \"" + row['parent-FQN'] + "\",", line)
            if ("methodName = " in line):
              line = re.sub(r"=\s\"(.+)\",", "= \"" + row['method-name'] + "\",", line)
            if ("String rowInCSVFile" in line):
              values = []
              for col in df.columns:
                col_value = str(row[col])
                if "," in col_value:
                  col_value = "\\\"" + col_value.replace(" ", "") + "\\\""
                values.append(col_value)
              row_as_string = ','.join(values)
              line = re.sub(r"\"\"", "\"" + row_as_string + "\"", line)
            # Changes needed for void methods
            if row['return-type'] == "void":
              if "boolean isReturnTypeVoid" in line:
                line = line.replace("false", "true")
              # if "@OnReturn" in line:
              if "onReturn(@BindReturn Object returnedObject," in line:
                line = line.replace("@BindReturn Object returnedObject", "@BindReceiver Object receivingObjectPost")
              if "writeObjectXMLToFile(returnedObject, returnedObjectFilePath)" in line:
                line = line.replace("returnedObject", "receivingObjectPost")
            if ("methodParameterTypes = " in line):
              if (pd.isnull(row['param-list'])):
                param_list = ""
              else:
                param_list = sanitize_parameter_list(row['param-list'])
              line = re.sub(r"=\s{.*},", "= {" + param_list + "},", line)
            if ("timerName = " in line):
              line = re.sub(r"=\s\".+\"\)", "= \"" + row['parent-FQN'] + "-" + row['method-name'] + "\")", line)
            if ("int COUNT = " in line):
              line = re.sub(r"=\s\d+;", "= " + str(count) + ";", line)
            f.write(line)
  print("New aspect classes added in se.kth.castor.pankti.instrument.plugins")
  all_aspects = sorted(glob.glob(base_path + "*.java"), key=lambda x:float(re.findall("(\d+)",x)[0]))
  new_aspect_count = int(re.search(r"(\d+)", all_aspects[-1]).group())
  return new_aspect_count

def update_glowroot_plugin_json(aspect_count):
  aspect_path = "se.kth.castor.pankti.instrument.plugins.MethodAspect"
  plugin_json_path = "./src/main/resources/META-INF/glowroot.plugin.json"
  index = 0
  aspect_lines = ""
  for i in range(1, aspect_count + 1):
    aspect_lines += "    \"" + aspect_path + str(i) + "\""
    if i < aspect_count:
      aspect_lines += ","
    aspect_lines += "\n"
  with open(plugin_json_path, "r") as json_file:
    for num, line in enumerate(json_file, 1):
      if "aspects" in line:
        index = num
  # delete previous aspect list
  with open(plugin_json_path, "r+") as json_file:
    contents = json_file.readlines()
    updated_contents = [l for l in contents if "MethodAspect" not in l]
    json_file.seek(0)
    json_file.truncate()
    json_file.writelines(updated_contents)
  # update aspect list with generated aspects
  with open(plugin_json_path, "r+") as json_file:
    contents = json_file.readlines()
    contents.insert(index, aspect_lines)
    json_file.seek(0)
    json_file.writelines(contents)
  print("resources/META-INF/glowroot.plugin.json updated")

def main(argv):
  try:
    cut = ""
    if len(argv) == 3:
      cut = argv[2]
      print("Attempting to generate aspects for target methods within", cut)
    elif len(argv) == 2:
      print("CUT not provided, will generate aspects for all target methods")
    df = pd.read_csv(argv[1])
    print("input (rows, columns): ", df.shape)
    aspect_count = generate_aspects(df, cut)
    update_glowroot_plugin_json(aspect_count)
  except Exception as e:
    print("USAGE: python instrument.py </path/to/instrumentation/candidate/list>.csv <optional.fqn.of.class.under.test>")
    print(e)
    sys.exit()

if __name__ == "__main__":
  main(sys.argv)

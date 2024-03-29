import glob
import numpy as np
import pandas as pd
import re
import sys


def extract_from_nested_invocation_map(invocation_map):
  invocation_map_to_list = invocation_map.split("}, ")
  print("Found nested invocations", len(invocation_map_to_list))
  return invocation_map_to_list


def get_class_name_from_invocation_string(invocation):
  class_name = re.sub(r"(.+)(nestedInvocationDeclaringType=')(.+)(', nestedInvocationMethod.+)", r"\3", invocation)
  return class_name


def get_method_from_invocation_string(invocation):
  method = re.sub(r"(.+nestedInvocationMethod=)('\w+')(.+)", r"\2", invocation).replace("'", "")
  return method


def get_parameters_from_invocation_string(invocation):
  args = re.sub(r"(.+nestedInvocationParams='\[)(.*)(\]'.+)", r"\2", invocation).replace("\s", "")
  return args


def get_mode(invocation):
  mode = re.sub(r"(.+nestedInvocationMode=)('\w+')(.+)", r"\2", invocation).replace("'", "")
  return mode


def sanitize_parameter_list(param_list):
  parameters = param_list.replace("(", "").replace(")", "")
  parameters = re.sub(r"([a-zA-Z0-9\$\.\[\]]+)", "\"" + r"\1" + "\"", parameters)
  parameters = re.sub("\"[EKNTV]\"", "\"java.lang.Object\"", parameters)
  parameters = re.sub("\"[EKNTV]\[\]\"", "\"java.lang.Object[]\"", parameters)
  return parameters


# Generate aspect classes based on the MethodAspect0Nested0 template
def generate_mock_aspect_class(template_file_path, new_file_path, count, nested_count, nested_invocation,
                               invocation_on_library_method):
  print("Generating aspect class", new_file_path)
  class_name = get_class_name_from_invocation_string(nested_invocation)
  method_name = get_method_from_invocation_string(nested_invocation)
  param_list = get_parameters_from_invocation_string(nested_invocation)
  with open(template_file_path) as template:
    with open(new_file_path, "w") as f:
      for line in template:
        if ("public class MethodAspect0Nested0" in line):
          line = line.replace("Aspect0", "Aspect" + str(count))
          line = line.replace("Nested0", "Nested" + str(nested_count))
        if ("@Pointcut(className =" in line):
          line = re.sub(r"=\s\"(.+)\",", "= \"" + class_name + "\",", line)
        if ("methodName = " in line):
          line = re.sub(r"=\s\"(.+)\",", "= \"" + method_name + "\",", line)
        if ("methodParameterTypes = " in line):
          if (param_list == "()"):
            param_list = ""
          else:
            param_list = sanitize_parameter_list(param_list)
          line = re.sub(r"=\s{.*},", "= {" + param_list + "},", line)
        if ("timerName = " in line):
          line = re.sub(r"=\s\".+\"\)", "= \"" + class_name + "-" + method_name + "\")", line)
        if ("double COUNT = " in line):
          line = re.sub(r"=\s\d+;", "= " + str(count) + "." + str(nested_count) + ";", line)
        if ("MethodAspect0.TargetMethodAdvice" in line):
          line = line.replace("MethodAspect0", "MethodAspect" + str(count))
        if (invocation_on_library_method and "boolean invocationOnLibraryMethod" in line):
          line = line.replace("false", "true")
        f.write(line)


# Generate aspect classes based on the MethodAspect0 template
def generate_aspect_class(template_file_path, new_file_path, count, row, df):
  print("Generating aspect class", new_file_path)
  with open(template_file_path) as template:
    with open(new_file_path, "w") as f:
      for line in template:
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
        # if method has mockable invocations
        if row['has-mockable-invocations']:
          if "boolean hasMockableInvocations" in line:
            line = line.replace("false", "true")
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


# Generate aspect classes
def generate_aspects(df, cut):
  base_path = "./src/main/java/se/kth/castor/pankti/instrument/plugins/MethodAspect"
  found_aspects = sorted(glob.glob(base_path + "*.java"), key=lambda x: float(re.findall("(\d+)", x)[0]))
  original_count = int(re.search(r"(\d+)", found_aspects[-1]).group())
  count = original_count
  aspects = []
  template_file_path = base_path + str(0) + ".java"
  mock_template_file_path = base_path + "0Nested0.java"
  df.replace(np.nan, '', regex=True, inplace=True)
  for index, row in df.iterrows():
    # temporarily, instrument classes with mockable invocations
    #     if row['visibility'] == "public":
    if row['has-mockable-invocations'] and (row['parent-FQN'] == cut if cut else True):
      count += 1
      aspects.append(str(count))
      new_file_path = base_path + str(count) + ".java"
      if (row['param-list'].startswith('[') and row['param-list'].endswith(']')):
        row['param-list'] = re.sub(r"^\[", "", row['param-list'])
        row['param-list'] = re.sub(r"\]$", "", row['param-list'])
      generate_aspect_class(template_file_path, new_file_path, count, row, df)
      if (row['has-mockable-invocations']):
        nested_invocations = extract_from_nested_invocation_map(row['nested-invocations'])
        for i in range(len(nested_invocations)):
          invocation_on_library_method = get_mode(nested_invocations[i]) == 'LIBRARY'
          nested_count = i + 1
          new_file_path = base_path + str(count) + "Nested" + str(nested_count) + ".java"
          aspects.append(str(count) + "." + str(nested_count))
          generate_mock_aspect_class(mock_template_file_path, new_file_path, count, nested_count,
                                     nested_invocations[i],
                                     invocation_on_library_method)
  if (original_count == count):
    print("No new aspect classes added in se.kth.castor.pankti.instrument.plugins - "
          "You may want to check the CUT again. Escape $ signs in the CUT name with a \\")
  else:
    print(f'{count - original_count} new aspect classes added in se.kth.castor.pankti.instrument.plugins')
  return aspects


# Update Glowroot plugin
def update_glowroot_plugin_json(aspects):
  aspect_path = "se.kth.castor.pankti.instrument.plugins.MethodAspect"
  plugin_json_path = "./src/main/resources/META-INF/glowroot.plugin.json"
  index = 0
  aspect_lines = ""
  has_existing_aspects = False
  last_existing_aspect = ""
  for i in range(len(aspects)):
    whole = re.sub(r"(\d+)(\.*)(\d*)", r"\1", str(aspects[i]))
    frac = re.sub(r"(\d+)(\.*)(\d*)", r"\3", str(aspects[i]))
    if frac == '':
      aspect_lines += "    \"" + aspect_path + whole + "\""
    else:
      aspect_lines += "    \"" + aspect_path + whole + "Nested" + frac + "\""
    if i < len(aspects) - 1:
      aspect_lines += ","
    aspect_lines += "\n"
  with open(plugin_json_path, "r+") as json_file:
    for num, line in enumerate(json_file, 1):
      if "aspects" in line:
        index = num
      if re.search(r"MethodAspect.+\"[^,]", line):
        index = num - 1
        has_existing_aspects = True
        last_existing_aspect = line
  if has_existing_aspects:
    with open(plugin_json_path, "r+") as json_file:
      contents = json_file.readlines()
      json_file.seek(0)
      for line in contents:
        if line != last_existing_aspect:
          json_file.write(line)
      json_file.truncate()
  with open(plugin_json_path, "r+") as json_file:
    contents = json_file.readlines()
    if has_existing_aspects:
      aspect_lines = last_existing_aspect.replace("\n", ",\n") + aspect_lines
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
    aspects = generate_aspects(df, cut)
    if (len(aspects) > 0):
      update_glowroot_plugin_json(aspects)
  except Exception as e:
    print(
      "USAGE: python instrument-mock.py </path/to/instrumentation/candidate/list>.csv <optional.fqn.of.class.under.test>")
    print(e)
    sys.exit()


if __name__ == "__main__":
  main(sys.argv)

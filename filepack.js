const fs = require("fs");
const path = require("path");

/**
 * Finds all files with specified extensions in a given directory and its subdirectories.
 * @param {string} startPath The starting directory to search.
 * @param {Array<string>} extensions An array of file extensions (e.g., ['.java', '.yml']).
 * @param {Array<string>} excludeFolders An array of folder names to exclude from search.
 * @returns {Array<string>} An array of full file paths.
 */
function findFiles(startPath, extensions, excludeFolders = []) {
  let results = [];
  const files = fs.readdirSync(startPath);

  for (const file of files) {
    const filePath = path.join(startPath, file);
    const stat = fs.statSync(filePath);

    if (stat.isDirectory()) {
      // Check if this directory should be excluded
      const shouldExclude = excludeFolders.some((excludeFolder) => {
        const folderName = path.basename(filePath);
        return folderName === excludeFolder || filePath.includes(excludeFolder);
      });

      if (!shouldExclude) {
        results = results.concat(
          findFiles(filePath, extensions, excludeFolders)
        ); // Recurse into subdirectories
      }
    } else if (extensions.some((ext) => filePath.endsWith(ext))) {
      results.push(filePath);
    }
  }
  return results;
}

/**
 * Removes comments from file content based on file type and replaces single quotes.
 * @param {string} content The file content as a string.
 * @param {string} filePath The full path of the file to determine its type.
 * @returns {string} The processed content.
 */
function processContent(content, filePath) {
  let processed = content;
  const ext = path.extname(filePath).toLowerCase();

  // 1. Remove comments
  if (ext === ".java") {
    // Remove multi-line comments /* ... */
    processed = processed.replace(/\/\*[\s\S]*?\*\//g, "");
    // Remove single-line comments //
    processed = processed.replace(/\/\/.*$/gm, "");
  } else if (ext === ".yml" || ext === ".yaml" || ext === ".properties") {
    // Remove single-line comments starting with #
    processed = processed.replace(/^\s*#.*$/gm, "");
  }
  // Note: This comment removal attempts to be robust but might not handle all edge cases
  // like comments inside strings perfectly without a full parser.

  // 2. Replace all single quotes with double single quotes ''
  processed = processed.replace(/ +/g, "  ");

  return processed;
}

/**
 * Main utility function to find, merge, process, and save files.
 * @param {string} entryFolderPath The path to the folder to start searching from.
 * @param {Array<string>} excludeFolders An array of folder names to exclude from search.
 */
function runUtility(entryFolderPath, excludeFolders = []) {
  const validExtensions = [".java", ".yml", ".yaml", ".properties"];
  const foundFiles = findFiles(
    entryFolderPath,
    validExtensions,
    excludeFolders
  );

  let mergedContent = [`# ${path.basename(entryFolderPath)} File List\n`];

  for (const filePath of foundFiles) {
    try {
      const fileContent = fs.readFileSync(filePath, "utf8");
      // Clean any surrogate characters from individual files
      const cleanFileContent = fileContent.replace(/[\uD800-\uDFFF]/g, "");
      const processedFileContent = processContent(cleanFileContent, filePath);
      const fileType = path.extname(filePath).toLowerCase().replace(".", "");
      mergedContent.push(
        `--- File: ${filePath} ---\n\n\`\`\` ${fileType}\n${processedFileContent}\`\`\`\n`
      );
    } catch (error) {
      console.warn(
        `Warning: Could not process file ${filePath}: ${error.message}`
      );
      // Add a placeholder for failed files
      mergedContent.push(
        `--- File: ${filePath} (ERROR: Could not read) ---\n\n\`\`\`\n[File could not be read: ${error.message}]\n\`\`\`\n`
      );
    }
  }

  const now = new Date();
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const day = String(now.getDate()).padStart(2, "0");
  const hours = String(now.getHours()).padStart(2, "0");
  const minutes = String(now.getMinutes()).padStart(2, "0");

  const entryFolderName = path.basename(entryFolderPath);
  const outputFileName = `${entryFolderName}-${month}${day}-${hours}${minutes}.md`;
  const outputPath = path.join(entryFolderPath, outputFileName); // Or specify a different output directory

  // Ensure proper UTF-8 encoding by replacing any surrogate characters
  const finalContent = mergedContent.join("\n");
  const cleanContent = finalContent.replace(/[\uD800-\uDFFF]/g, "");
  fs.writeFileSync(outputPath, cleanContent, "utf8");
  console.log(`Successfully merged and processed content into: ${outputPath}`);
  console.log(`Processed ${foundFiles.length} files.`);
}

// --- Usage Example ---
// To run this script:
// 1. Save it as a .js file (e.g., `filepack.js`).
// 2. Open your terminal.
// 3. Navigate to the directory containing the script.
// 4. Run it using Node.js, providing the target folder as an argument:
//    node filepack.js /path/to/your/entry/folder [exclude_folders]
//
// Examples:
//    node filepack.js ./my-project-source
//    node filepack.js ./my-project-source 'node_modules,build,target'
//    node filepack.js ./my-project-source 'bin,out,dist,.git'
//
// The exclude_folders parameter is optional and should be a comma-separated list
// of folder names to exclude from the search. Both exact folder names and
// partial path matches are supported.

// Parse command line arguments
const entryFolder = process.argv[2]; // Get folder path from command line argument
const excludeFoldersArg = process.argv[3]; // Get exclude folders from command line argument

if (!entryFolder) {
  console.error("Please provide an entry folder path as an argument.");
  console.error(
    "Usage: node your_script_name.js /path/to/your/folder [exclude_folders]"
  );
  console.error(
    "Example: node filepack.js ./my-project 'node_modules,build,target'"
  );
  process.exit(1);
}

if (!fs.existsSync(entryFolder) || !fs.statSync(entryFolder).isDirectory()) {
  console.error(
    `Error: The provided path "${entryFolder}" is not a valid directory.`
  );
  process.exit(1);
}

// Parse exclude folders from command line argument
let excludeFolders = [];
if (excludeFoldersArg) {
  excludeFolders = excludeFoldersArg.split(",").map((folder) => folder.trim());
  console.log(`Excluding folders: ${excludeFolders.join(", ")}`);
}

runUtility(entryFolder, excludeFolders);

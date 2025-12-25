import jp from 'jsonpath';

/**
 * Wrapper function to match the original jsonPath API
 * Converts path segments with special characters to bracket notation
 * e.g., $.a.b{c}.d -> $.a['b{c}'].d
 */
export function jsonPath(obj, path, defaultValue = null) {
    try {
        // Convert path segments with special characters to bracket notation
        let convertedPath = path;
        
        // Match segments that contain special characters like {, }, etc.
        // and convert them to bracket notation
        convertedPath = convertedPath.replace(/\.([^.\[\]$]+[{}\s][^.\[\]$]*)/g, (match, segment) => {
            return `['${segment}']`;
        });
        
        const result = jp.query(obj, convertedPath);
        if (result.length === 0) {
            return defaultValue;
        }
        // If the path returns a single value, return it directly
        // Otherwise return the first result (matching original behavior)
        return result.length === 1 ? result[0] : result[0];
    } catch (error) {
        return defaultValue;
    }
}

/**
 * Helper to get map/object size
 */
export function mapSize(obj) {
    return obj ? Object.keys(obj).length : 0;
}

/**
 * Helper to get array size
 */
export function arraySize(arr) {
    return arr ? arr.length : 0;
}


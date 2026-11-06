import Foundation

enum CSVReader {
    static func rows(from text: String) -> [[String]] {
        var rows: [[String]] = []
        var row: [String] = []
        var field = ""
        var quoted = false
        let chars = Array(text.replacingOccurrences(of: "\r\n", with: "\n").replacingOccurrences(of: "\r", with: "\n"))
        var index = 0

        while index < chars.count {
            let ch = chars[index]
            if ch == "\"" {
                if quoted && index + 1 < chars.count && chars[index + 1] == "\"" {
                    field.append("\"")
                    index += 1
                } else { quoted.toggle() }
            } else if ch == "," && !quoted {
                row.append(field); field = ""
            } else if ch == "\n" && !quoted {
                row.append(field)
                if row.contains(where: { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }) { rows.append(row) }
                row = []; field = ""
            } else { field.append(ch) }
            index += 1
        }
        row.append(field)
        if row.contains(where: { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }) { rows.append(row) }
        return rows
    }
}

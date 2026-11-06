import Foundation

struct DhikrItem: Identifiable, Hashable {
    let category: String
    let text: String
    let count: Int
    let id: String
}

struct RemainingItem: Identifiable, Hashable {
    let name: String
    let hijriDate: String
    let gregorianDate: String
    let date: Date
    var id: String { "\(name)-\(date.timeIntervalSince1970)" }
}

enum NumberedData {
    private static func resource(_ name: String) -> String? {
        Bundle.main.url(forResource: name, withExtension: nil)
            .flatMap { try? String(contentsOf: $0, encoding: .utf8) }
    }

    static func loadDhikr() -> [DhikrItem] {
        guard let csv = resource(NumberedShared.dhikrCSV) else { return [] }
        return CSVReader.rows(from: csv).compactMap { row in
            guard row.count >= 3 else { return nil }
            let category = row[0].trimmingCharacters(in: .whitespacesAndNewlines)
            let text = row[1].trimmingCharacters(in: .whitespacesAndNewlines)
            let count = arabicInt(row[2])
            let id = row.count > 3 ? row[3].trimmingCharacters(in: .whitespacesAndNewlines) : UUID().uuidString
            guard !category.isEmpty, !text.isEmpty, count > 0 else { return nil }
            if category.contains("الفئة") || text.contains("الذكر") { return nil }
            return DhikrItem(category: category, text: text, count: count, id: id.isEmpty ? UUID().uuidString : id)
        }
    }

    static func loadRemaining() -> [RemainingItem] {
        guard let csv = resource(NumberedShared.remainingCSV) else { return [] }
        return CSVReader.rows(from: csv).compactMap { row in
            guard row.count >= 3, let date = parseArabicDate(row[2]) else { return nil }
            return RemainingItem(name: row[0].trimmingCharacters(in: .whitespacesAndNewlines),
                                 hijriDate: row[1].trimmingCharacters(in: .whitespacesAndNewlines),
                                 gregorianDate: row[2].trimmingCharacters(in: .whitespacesAndNewlines), date: date)
        }.sorted { $0.date < $1.date }
    }

    static func nextRemaining(now: Date = .now) -> RemainingItem? {
        loadRemaining().first { $0.date > now }
    }

    static func arabicInt(_ value: String) -> Int {
        let map: [Character: Character] = ["٠":"0","١":"1","٢":"2","٣":"3","٤":"4","٥":"5","٦":"6","٧":"7","٨":"8","٩":"9"]
        return Int(String(value.map { map[$0] ?? $0 }).replacingOccurrences(of: "٬", with: "").replacingOccurrences(of: ",", with: "")) ?? 0
    }

    static func parseArabicDate(_ value: String) -> Date? {
        var text = value.trimmingCharacters(in: .whitespacesAndNewlines).replacingOccurrences(of: "م", with: "")
        let months = ["يناير":"January","فبراير":"February","مارس":"March","أبريل":"April","ابريل":"April",
                      "مايو":"May","يونيو":"June","يوليو":"July","أغسطس":"August","اغسطس":"August",
                      "سبتمبر":"September","أكتوبر":"October","اكتوبر":"October","نوفمبر":"November","ديسمبر":"December"]
        for (ar, en) in months { text = text.replacingOccurrences(of: ar, with: en) }
        let map: [Character: Character] = ["٠":"0","١":"1","٢":"2","٣":"3","٤":"4","٥":"5","٦":"6","٧":"7","٨":"8","٩":"9"]
        text = String(text.map { map[$0] ?? $0 }).trimmingCharacters(in: .whitespaces)
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(identifier: "Asia/Riyadh")
        formatter.isLenient = false
        for format in ["d MMMM yyyy", "dd MMMM yyyy", "d MMM yyyy", "dd MMM yyyy"] {
            formatter.dateFormat = format
            if let date = formatter.date(from: text) {
                var calendar = Calendar(identifier: .gregorian)
                calendar.timeZone = TimeZone(identifier: "Asia/Riyadh") ?? .current
                return calendar.startOfDay(for: date)
            }
        }
        return nil
    }
}

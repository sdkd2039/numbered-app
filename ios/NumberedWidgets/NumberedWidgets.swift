import WidgetKit
import SwiftUI
import AppIntents

struct NumberedEntry: TimelineEntry {
    let date: Date
    let category: String
    let dhikr: String
    let count: Int
    let target: Int
    let eventName: String
    let hijriDate: String
    let eventDate: Date
}

struct NumberedProvider: TimelineProvider {
    func placeholder(in context: Context) -> NumberedEntry {
        NumberedEntry(date: .now, category: "الصباح", dhikr: "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ",
                      count: 0, target: 100, eventName: "شهر رمضان", hijriDate: "1 رمضان",
                      eventDate: Calendar.current.date(byAdding: .day, value: 30, to: .now)!)
    }
    func getSnapshot(in context: Context, completion: @escaping (NumberedEntry) -> Void) { completion(makeEntry()) }
    func getTimeline(in context: Context, completion: @escaping (Timeline<NumberedEntry>) -> Void) {
        let entry = makeEntry()
        completion(Timeline(entries: [entry], policy: .after(Calendar.current.date(byAdding: .minute, value: 30, to: .now)!)))
    }
    private func makeEntry() -> NumberedEntry {
        let defaults = NumberedShared.defaults
        let category = defaults.string(forKey: "dhikrCategory") ?? "الصباح"
        let items = NumberedData.loadDhikr().filter { $0.category == category }
        let index = defaults.integer(forKey: "dhikrIndex")
        let item = items.indices.contains(index) ? items[index] : items.first
        let event = NumberedData.nextRemaining() ?? NumberedData.loadRemaining().first
        return NumberedEntry(date: .now, category: category, dhikr: item?.text ?? "افتح التطبيق لعرض الأذكار",
                             count: defaults.integer(forKey: "dhikrCount"), target: item?.count ?? 1,
                             eventName: event?.name ?? "لا توجد مناسبات", hijriDate: event?.hijriDate ?? "",
                             eventDate: event?.date ?? .now)
    }
}

struct DhikrWidgetView: View {
    let entry: NumberedEntry
    var body: some View {
        VStack(alignment: .trailing, spacing: 8) {
            HStack { Text("معدودات").font(.caption.bold()); Spacer(); Text(entry.category).font(.caption).foregroundStyle(.secondary) }
            Text(entry.dhikr).font(.system(size: 15, weight: .medium)).multilineTextAlignment(.trailing)
                .lineLimit(4).frame(maxWidth: .infinity, alignment: .trailing)
            HStack {
                Text("\(entry.count) / \(entry.target)").font(.caption.bold()).monospacedDigit()
                Spacer()
                Button(intent: IncrementDhikrIntent()) { Image(systemName: "plus.circle.fill") }.buttonStyle(.plain)
                Button(intent: NextDhikrIntent()) { Image(systemName: "forward.end.fill") }.buttonStyle(.plain)
            }
            ProgressView(value: Double(min(entry.count, entry.target)), total: Double(max(1, entry.target)))
        }.padding()
    }
}

struct RemainingWidgetView: View {
    let entry: NumberedEntry
    private var parts: (Int, Int, Int) {
        let seconds = max(0, Int(entry.eventDate.timeIntervalSince(entry.date)))
        return (seconds / 86400, (seconds % 86400) / 3600, (seconds % 3600) / 60)
    }
    var body: some View {
        VStack(alignment: .trailing, spacing: 7) {
            Text("المتبقي").font(.caption.bold())
            Text(entry.eventName).font(.headline)
            Text(entry.hijriDate).font(.caption).foregroundStyle(.secondary)
            HStack { unit(parts.0, "يوم"); unit(parts.1, "ساعة"); unit(parts.2, "دقيقة") }
        }.padding()
    }
    private func unit(_ number: Int, _ title: String) -> some View {
        VStack { Text("\(number)").font(.title3.bold()).monospacedDigit(); Text(title).font(.caption2) }.frame(maxWidth: .infinity)
    }
}

struct DhikrWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "NumberedDhikrWidget", provider: NumberedProvider()) { entry in DhikrWidgetView(entry: entry) }
            .configurationDisplayName("أذكار معدودات").description("أذكار الصباح والمساء والمنوعة مع عداد.")
            .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
    }
}
struct RemainingWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "NumberedRemainingWidget", provider: NumberedProvider()) { entry in RemainingWidgetView(entry: entry) }
            .configurationDisplayName("المتبقي").description("عدّ تنازلي للمناسبة القادمة.")
            .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
    }
}

@main
struct NumberedWidgets: WidgetBundle {
    var body: some Widget { DhikrWidget(); RemainingWidget() }
}

struct IncrementDhikrIntent: AppIntent {
    static var title: LocalizedStringResource = "زيادة عداد الذكر"
    func perform() async throws -> some IntentResult {
        let defaults = NumberedShared.defaults
        let category = defaults.string(forKey: "dhikrCategory") ?? "الصباح"
        let list = NumberedData.loadDhikr().filter { $0.category == category }
        let index = defaults.integer(forKey: "dhikrIndex")
        guard list.indices.contains(index) else { return .result() }
        let old = defaults.integer(forKey: "dhikrCount")
        defaults.set(min(list[index].count, old + 1), forKey: "dhikrCount")
        WidgetCenter.shared.reloadAllTimelines()
        return .result()
    }
}

struct NextDhikrIntent: AppIntent {
    static var title: LocalizedStringResource = "الذكر التالي"
    func perform() async throws -> some IntentResult {
        let defaults = NumberedShared.defaults
        let category = defaults.string(forKey: "dhikrCategory") ?? "الصباح"
        let items = NumberedData.loadDhikr().filter { $0.category == category }
        guard !items.isEmpty else { return .result() }
        defaults.set((defaults.integer(forKey: "dhikrIndex") + 1) % items.count, forKey: "dhikrIndex")
        defaults.set(0, forKey: "dhikrCount")
        WidgetCenter.shared.reloadAllTimelines()
        return .result()
    }
}

import SwiftUI

struct ContentView: View {
    @State private var category = "الصباح"
    @State private var index = 0
    @State private var count = 0
    @State private var selectedTab = 0

    private let items = NumberedData.loadDhikr()
    private let categories = ["الصباح", "المساء", "منوعة"]
    private var filtered: [DhikrItem] { items.filter { $0.category == category } }
    private var current: DhikrItem? { filtered.indices.contains(index) ? filtered[index] : nil }

    var body: some View {
        TabView(selection: $selectedTab) {
            dhikrView.tabItem { Label("الأذكار", systemImage: "hands.sparkles") }.tag(0)
            remainingView.tabItem { Label("المتبقي", systemImage: "hourglass") }.tag(1)
            aboutView.tabItem { Label("عن معدودات", systemImage: "info.circle") }.tag(2)
        }
        .tint(Color(red: 0.22, green: 0.39, blue: 0.72))
        .onChange(of: category) { _, _ in index = 0; count = 0 }
    }

    private var dhikrView: some View {
        NavigationStack {
            VStack(spacing: 20) {
                Picker("الفئة", selection: $category) {
                    ForEach(categories, id: \.self) { Text($0).tag($0) }
                }.pickerStyle(.segmented)
                if let item = current {
                    VStack(spacing: 16) {
                        Text(item.category).font(.subheadline.weight(.semibold)).foregroundStyle(.secondary)
                        Text(item.text).font(.system(size: 23, weight: .medium, design: .rounded))
                            .multilineTextAlignment(.center).lineSpacing(9)
                            .padding(.horizontal, 8).frame(maxWidth: .infinity, minHeight: 190)
                        Text("\(count) / \(item.count)").font(.system(size: 27, weight: .bold, design: .rounded)).monospacedDigit()
                        ProgressView(value: Double(min(count, item.count)), total: Double(item.count)).tint(.blue)
                        Button {
                            count = count >= item.count ? 0 : count + 1
                        } label: {
                            Text(count >= item.count ? "إعادة العداد" : "تسبيح").font(.title3.bold())
                                .frame(maxWidth: .infinity).padding(.vertical, 17)
                                .background(Color.blue.gradient, in: RoundedRectangle(cornerRadius: 18)).foregroundStyle(.white)
                        }
                        HStack {
                            Button("السابق") { move(-1) }.buttonStyle(.bordered)
                            Spacer()
                            Text("\(index + 1) من \(filtered.count)").foregroundStyle(.secondary)
                            Spacer()
                            Button("التالي") { move(1) }.buttonStyle(.bordered)
                        }
                    }.padding(22).background(.regularMaterial, in: RoundedRectangle(cornerRadius: 26))
                } else {
                    ContentUnavailableView("لا توجد أذكار", systemImage: "text.book.closed")
                }
                Spacer(minLength: 0)
            }.padding().navigationTitle("معدودات")
        }
    }

    private func move(_ offset: Int) {
        guard !filtered.isEmpty else { return }
        index = (index + offset + filtered.count) % filtered.count
        count = 0
    }

    private var remainingView: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 18) {
                    if let event = NumberedData.nextRemaining() {
                        VStack(spacing: 12) {
                            Text("المناسبة القادمة").font(.headline).foregroundStyle(.secondary)
                            Text(event.name).font(.largeTitle.bold())
                            Text(event.hijriDate).font(.title3).foregroundStyle(.secondary)
                            RemainingCountdown(date: event.date)
                            Text(event.gregorianDate).font(.callout).foregroundStyle(.secondary)
                        }.frame(maxWidth: .infinity).padding(24).background(.regularMaterial, in: RoundedRectangle(cornerRadius: 26))
                    } else {
                        ContentUnavailableView("لا توجد مناسبات قادمة", systemImage: "calendar")
                    }
                    VStack(alignment: .trailing, spacing: 12) {
                        Text("المناسبات القادمة").font(.title2.bold())
                        ForEach(NumberedData.loadRemaining().filter { $0.date > .now }) { event in
                            HStack {
                                VStack(alignment: .trailing, spacing: 4) {
                                    Text(event.name).font(.headline)
                                    Text(event.hijriDate).font(.caption).foregroundStyle(.secondary)
                                }
                                Spacer()
                                Text(event.gregorianDate).font(.caption)
                            }.padding().background(.thinMaterial, in: RoundedRectangle(cornerRadius: 14))
                        }
                    }
                }.padding()
            }.navigationTitle("المتبقي")
        }
    }

    private var aboutView: some View {
        NavigationStack {
            VStack(spacing: 16) {
                Image(systemName: "sparkles").font(.system(size: 54)).foregroundStyle(.blue)
                Text("معدودات").font(.largeTitle.bold())
                Text("الأذكار والمناسبات في مكان واحد").font(.title3).foregroundStyle(.secondary)
                Text("نسخة iOS").font(.footnote).foregroundStyle(.tertiary)
            }.frame(maxWidth: .infinity, maxHeight: .infinity).navigationTitle("عن التطبيق")
        }
    }
}

private struct RemainingCountdown: View {
    let date: Date
    @State private var now = Date.now
    private let timer = Timer.publish(every: 60, on: .main, in: .common).autoconnect()
    private var parts: (Int, Int, Int) {
        let seconds = max(0, Int(date.timeIntervalSince(now)))
        return (seconds / 86400, (seconds % 86400) / 3600, (seconds % 3600) / 60)
    }
    var body: some View {
        HStack(spacing: 12) {
            unit(parts.0, "يوم"); unit(parts.1, "ساعة"); unit(parts.2, "دقيقة")
        }.onReceive(timer) { _ in now = .now }
    }
    private func unit(_ value: Int, _ title: String) -> some View {
        VStack(spacing: 5) {
            Text("\(value)").font(.system(size: 30, weight: .bold, design: .rounded)).monospacedDigit()
            Text(title).font(.caption).foregroundStyle(.secondary)
        }.frame(maxWidth: .infinity).padding(12).background(.thinMaterial, in: RoundedRectangle(cornerRadius: 14))
    }
}
